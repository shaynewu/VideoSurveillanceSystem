/*******************************************************************************
#                                                                              #
#      MJPG-streamer allows to stream JPG frames from an input-plugin          #
#      to several output plugins                                               #
#                                                                              #
#      Copyright (C) 2007 busybox-project (base64 function)                    #
#      Copyright (C) 2007 Tom St枚veken                                         #
#                                                                              #
# This program is free software; you can redistribute it and/or modify         #
# it under the terms of the GNU General Public License as published by         #
# the Free Software Foundation; version 2 of the License.                      #
#                                                                              #
# This program is distributed in the hope that it will be useful,              #
# but WITHOUT ANY WARRANTY; without even the implied warranty of               #
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                #
# GNU General Public License for more details.                                 #
#                                                                              #
# You should have received a copy of the GNU General Public License            #
# along with this program; if not, write to the Free Software                  #
# Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA    #
#                                                                              #
*******************************************************************************/
#include <string.h>
#include <sys/time.h>
#include <sys/types.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdio.h>
#include <pthread.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <syslog.h>

#include "../../mjpg_streamer.h"
#include "../../utils.h"
#include "httpd.h"

static globals *pglobal;
extern context servers[MAX_OUTPUT_PLUGINS];

/******************************************************************************
Description.: initializes the iobuffer structure properly
Input Value.: pointer to already allocated iobuffer
Return Value: iobuf
******************************************************************************/
void init_iobuffer(iobuffer *iobuf)
{
	memset(iobuf->buffer, 0, sizeof(iobuf->buffer));
	iobuf->level = 0;
}

/******************************************************************************
Description.: initializes the request structure properly
Input Value.: pointer to already allocated req
Return Value: req
******************************************************************************/
void init_request(request *req) {
  req->type        = A_UNKNOWN;
  req->parameter   = NULL;
  req->client      = NULL;
  req->credentials = NULL;
}

/******************************************************************************
Description.: If strings were assigned to the different members free them
              This will fail if strings are static, so always use strdup().
Input Value.: req: pointer to request structure
Return Value: -
******************************************************************************/
void free_request(request *req) {
  if ( req->parameter != NULL ) free(req->parameter);
  if ( req->client != NULL ) free(req->client);
  if ( req->credentials != NULL ) free(req->credentials);
}

/******************************************************************************
Description.: read with timeout, implemented without using signals
              tries to read len bytes and returns if enough bytes were read
              or the timeout was triggered. In case of timeout the return
              value may differ from the requested bytes "len".
Input Value.: * fd.....: fildescriptor to read from
              * iobuf..: iobuffer that allows to use this functions from multiple
                         threads because the complete context is the iobuffer.
              * buffer.: The buffer to store values at, will be set to zero
                         before storing values.
              * len....: the length of buffer
              * timeout: seconds to wait for an answer
Return Value: * buffer.: will become filled with bytes read
              * iobuf..: May get altered to save the context for future calls.
              * func().: bytes copied to buffer or -1 in case of error
******************************************************************************/
int _read(int fd, iobuffer *iobuf, void *buffer, size_t len, int timeout)
{
	int copied=0, rc, i;
	fd_set fds;
	struct timeval tv;

	memset(buffer, 0, len);

	while ( (copied < len) )
	{
		i = MIN(iobuf->level, len-copied);	// 第一次,i=0,以后，i=1
		memcpy(buffer+copied, iobuf->buffer+IO_BUFFER-iobuf->level, i);

		iobuf->level -= i;
		copied += i;
		if ( copied >= len )
			return copied;

		/* select will return in case of timeout or new data arrived */
		/*
			当客服端发有数据或者超时的时候，select函数就返回
		*/
		tv.tv_sec = timeout;
		tv.tv_usec = 0;
		FD_ZERO(&fds);
		FD_SET(fd, &fds);
		if ( (rc = select(fd+1, &fds, NULL, NULL, &tv)) <= 0 )
		{
			if ( rc < 0)
			exit(EXIT_FAILURE);

			/* this must be a timeout */
			return copied;
		}

		init_iobuffer(iobuf);	// 将 iobuf 清0

		/*
		* there should be at least one byte, because select signalled it.
		* But: It may happen (very seldomly), that the socket gets closed remotly between
		* the select() and the following read. That is the reason for not relying
		* on reading at least one byte.
		*/
		/*
			调用read函数,从客服端读取最多 256 字节的数据
		*/
		if ( (iobuf->level = read(fd, &iobuf->buffer, IO_BUFFER)) <= 0 )
		{
			/* an error occured */
			return -1;
		}

		/* align data to the end of the buffer if less than IO_BUFFER bytes were read */
		/* 拷贝iobuf->buffer中的数据 */
		memmove(iobuf->buffer+(IO_BUFFER-iobuf->level), iobuf->buffer, iobuf->level);
	}

	return 0;
}

/******************************************************************************
Description.: Read a single line from the provided fildescriptor.
              This funtion will return under two conditions:
              * line end was reached
              * timeout occured
Input Value.: * fd.....: fildescriptor to read from
              * iobuf..: iobuffer that allows to use this functions from multiple
                         threads because the complete context is the iobuffer.
              * buffer.: The buffer to store values at, will be set to zero
                         before storing values.
              * len....: the length of buffer
              * timeout: seconds to wait for an answer
Return Value: * buffer.: will become filled with bytes read
              * iobuf..: May get altered to save the context for future calls.
              * func().: bytes copied to buffer or -1 in case of error
******************************************************************************/
/* read just a single line or timeout */
int _readline(int fd, iobuffer *iobuf, void *buffer, size_t len, int timeout)
{
	char c='\0', *out=buffer;
	int i;

	/*
		_readline:
			从iobuf.buf[]中一个字节一个字节的将数据取出存放到buffer中,直到遇见
		换行符号'\n'或者长度达到了.
	*/

	memset(buffer, 0, len);

	for ( i=0; i<len && c != '\n'; i++ )
	{
		if ( _read(fd, iobuf, &c, 1, timeout) <= 0 )
		{
			/* timeout or error occured */
			return -1;
		}
		*out++ = c;
	}

	return i;
}

/******************************************************************************
Description.: Decodes the data and stores the result to the same buffer.
              The buffer will be large enough, because base64 requires more
              space then plain text.
Hints.......: taken from busybox, but it is GPL code
Input Value.: base64 encoded data
Return Value: plain decoded data
******************************************************************************/
void decodeBase64(char *data) {
  const unsigned char *in = (const unsigned char *)data;
  /* The decoded size will be at most 3/4 the size of the encoded */
  unsigned ch = 0;
  int i = 0;

  while (*in) {
    int t = *in++;

    if (t >= '0' && t <= '9')
      t = t - '0' + 52;
    else if (t >= 'A' && t <= 'Z')
      t = t - 'A';
    else if (t >= 'a' && t <= 'z')
      t = t - 'a' + 26;
    else if (t == '+')
      t = 62;
    else if (t == '/')
      t = 63;
    else if (t == '=')
      t = 0;
    else
      continue;

    ch = (ch << 6) | t;
    i++;
    if (i == 4) {
      *data++ = (char) (ch >> 16);
      *data++ = (char) (ch >> 8);
      *data++ = (char) ch;
      i = 0;
    }
  }
  *data = '\0';
}

/******************************************************************************
Description.: Send a complete HTTP response and a single JPG-frame.
Input Value.: fildescriptor fd to send the answer to
Return Value: -
******************************************************************************/
void send_snapshot(int fd)
{
	unsigned char *frame=NULL;
	int frame_size=0;
	char buffer[BUFFER_SIZE] = {0};

	/* wait for a fresh frame */
	/* 等待输入通道发送数据更新请求 */
	pthread_cond_wait(&pglobal->db_update, &pglobal->db);

	/* read buffer */
	frame_size = pglobal->size;		// 得到一帧数据的大小

	/* allocate a buffer for this single frame */
	/* 根据一帧数据的大小,分配一个 frame 缓冲区 */
	if ( (frame = malloc(frame_size+1)) == NULL )
	{
		free(frame);
		pthread_mutex_unlock( &pglobal->db );
		send_error(fd, 500, "not enough memory");
		return;
	}

	/* 从仓库(pglobal->buf)中取出数据 */
	memcpy(frame, pglobal->buf, frame_size);
	DBG("got frame (size: %d kB)\n", frame_size/1024);

	pthread_mutex_unlock( &pglobal->db );

	/* write the response */
	/* 让buffer = "" */
	sprintf(buffer, "HTTP/1.0 200 OK\r\n" \
	  STD_HEADER \
	  "Content-type: image/jpeg\r\n" \
	  "\r\n");

	/* send header and image now */
	/* 将buffer中的字符串发送给客服端 */
	if( write(fd, buffer, strlen(buffer)) < 0 )
	{
		free(frame);
		return;
	}
	write(fd, frame, frame_size);	// 将一帧图片给发送出去

	free(frame);	// 释放缓冲区
}

/******************************************************************************
Description.: Send a complete HTTP response and a stream of JPG-frames.
Input Value.: fildescriptor fd to send the answer to
Return Value: -
******************************************************************************/
void send_stream(int fd)
{
	unsigned char *frame=NULL, *tmp=NULL;
	int frame_size=0, max_frame_size=0;
	char buffer[BUFFER_SIZE] = {0};

	DBG("preparing header\n");

	/* 让buffer = "" */
	sprintf(buffer, "HTTP/1.0 200 OK\r\n" \
	STD_HEADER \
	"Content-Type: multipart/x-mixed-replace;boundary=" BOUNDARY "\r\n" \
	"\r\n" \
	"--" BOUNDARY "\r\n");

	/* 将 buffer 中的字符串发送出去(报文) */
	if ( write(fd, buffer, strlen(buffer)) < 0 )
	{
		free(frame);
		return;
	}

	DBG("Headers send, sending stream now\n");

	while ( !pglobal->stop )
	{

		/* wait for fresh frames */
		/* 等待输入通道发出数据更新的信号 */
		pthread_cond_wait(&pglobal->db_update, &pglobal->db);

		/* read buffer */
		frame_size = pglobal->size;	// 得到一帧图片的大小

		/* check if framebuffer is large enough, increase it if necessary */
		/* 检查我们之前分配的缓存是否够大,如果不够,则重新分配 */
		if ( frame_size > max_frame_size )
		{
			DBG("increasing buffer size to %d\n", frame_size);

			max_frame_size = frame_size+TEN_K;
			if ( (tmp = realloc(frame, max_frame_size)) == NULL )	// 重新分配缓存
			{
				free(frame);
				pthread_mutex_unlock( &pglobal->db );
				send_error(fd, 500, "not enough memory");
				return;
			}

			frame = tmp;
		}

		/* 从仓库中取出一帧数据 */
		memcpy(frame, pglobal->buf, frame_size);
		DBG("got frame (size: %d kB)\n", frame_size/1024);

		pthread_mutex_unlock( &pglobal->db );

		/*
		* print the individual mimetype and the length
		* sending the content-length fixes random stream disruption observed
		* with firefox
		*/
		/* 让 buffer = ""报文，告诉客服端即将发送的图片的大小 */
		sprintf(buffer, "Content-Type: image/jpeg\r\n" \
		"Content-Length: %d\r\n" \
		"\r\n", frame_size);
		DBG("sending intemdiate header\n");
		if ( write(fd, buffer, strlen(buffer)) < 0 ) break;

		/* 将一帧图片发送出去 */
		DBG("sending frame\n");
		if( write(fd, frame, frame_size) < 0 ) break;

		/* 让 buffer = "boundarydonotcross" */
		DBG("sending boundary\n");
		sprintf(buffer, "\r\n--" BOUNDARY "\r\n");
		if ( write(fd, buffer, strlen(buffer)) < 0 ) break;
	}

	free(frame);	// 释放缓存
}

/******************************************************************************
Description.: Send error messages and headers.
Input Value.: * fd.....: is the filedescriptor to send the message to
              * which..: HTTP error code, most popular is 404
              * message: append this string to the displayed response
Return Value: -
******************************************************************************/
void send_error(int fd, int which, char *message) {
  char buffer[BUFFER_SIZE] = {0};

  if ( which == 401 ) {
    sprintf(buffer, "HTTP/1.0 401 Unauthorized\r\n" \
                    "Content-type: text/plain\r\n" \
                    STD_HEADER \
                    "WWW-Authenticate: Basic realm=\"MJPG-Streamer\"\r\n" \
                    "\r\n" \
                    "401: Not Authenticated!\r\n" \
                    "%s", message);
  } else if ( which == 404 ) {
    sprintf(buffer, "HTTP/1.0 404 Not Found\r\n" \
                    "Content-type: text/plain\r\n" \
                    STD_HEADER \
                    "\r\n" \
                    "404: Not Found!\r\n" \
                    "%s", message);
  } else if ( which == 500 ) {
    sprintf(buffer, "HTTP/1.0 500 Internal Server Error\r\n" \
                    "Content-type: text/plain\r\n" \
                    STD_HEADER \
                    "\r\n" \
                    "500: Internal Server Error!\r\n" \
                    "%s", message);
  } else if ( which == 400 ) {
    sprintf(buffer, "HTTP/1.0 400 Bad Request\r\n" \
                    "Content-type: text/plain\r\n" \
                    STD_HEADER \
                    "\r\n" \
                    "400: Not Found!\r\n" \
                    "%s", message);
  } else {
    sprintf(buffer, "HTTP/1.0 501 Not Implemented\r\n" \
                    "Content-type: text/plain\r\n" \
                    STD_HEADER \
                    "\r\n" \
                    "501: Not Implemented!\r\n" \
                    "%s", message);
  }

  write(fd, buffer, strlen(buffer));
}

/******************************************************************************
Description.: Send HTTP header and copy the content of a file. To keep things
              simple, just a single folder gets searched for the file. Just
              files with known extension and supported mimetype get served.
              If no parameter was given, the file "index.html" will be copied.
Input Value.: * fd.......: filedescriptor to send data to
              * parameter: string that consists of the filename
              * id.......: specifies which server-context is the right one
Return Value: -
******************************************************************************/
void send_file(int id, int fd, char *parameter) {
  char buffer[BUFFER_SIZE] = {0};
  char *extension, *mimetype=NULL;
  int i, lfd;
  config conf = servers[id].conf;

  /* in case no parameter was given */
  if ( parameter == NULL || strlen(parameter) == 0 )
    parameter = "index.html";

  /* find file-extension */
  if ( (extension = strstr(parameter, ".")) == NULL ) {
    send_error(fd, 400, "No file extension found");
    return;
  }

  /* determine mime-type */
  for ( i=0; i < LENGTH_OF(mimetypes); i++ ) {
    if ( strcmp(mimetypes[i].dot_extension, extension) == 0 ) {
      mimetype = (char *)mimetypes[i].mimetype;
      break;
    }
  }

  /* in case of unknown mimetype or extension leave */
  if ( mimetype == NULL ) {
    send_error(fd, 404, "MIME-TYPE not known");
    return;
  }

  /* now filename, mimetype and extension are known */
  DBG("trying to serve file \"%s\", extension: \"%s\" mime: \"%s\"\n", parameter, extension, mimetype);

  /* build the absolute path to the file */
  strncat(buffer, conf.www_folder, sizeof(buffer)-1);
  strncat(buffer, parameter, sizeof(buffer)-strlen(buffer)-1);

  /* try to open that file */
  if ( (lfd = open(buffer, O_RDONLY)) < 0 ) {
    DBG("file %s not accessible\n", buffer);
    send_error(fd, 404, "Could not open file");
    return;
  }
  DBG("opened file: %s\n", buffer);

  /* prepare HTTP header */
  sprintf(buffer, "HTTP/1.0 200 OK\r\n" \
                  "Content-type: %s\r\n" \
                  STD_HEADER \
                  "\r\n", mimetype);
  i = strlen(buffer);

  /* first transmit HTTP-header, afterwards transmit content of file */
  do {
    if ( write(fd, buffer, i) < 0 ) {
      close(lfd);
      return;
    }
  } while ( (i=read(lfd, buffer, sizeof(buffer))) > 0 );

  /* close file, job done */
  close(lfd);
}

/******************************************************************************
Description.: Perform a command specified by parameter. Send response to fd.
Input Value.: * fd.......: filedescriptor to send HTTP response to.
              * parameter: contains the command and value as string.
              * id.......: specifies which server-context to choose.
Return Value: -
******************************************************************************/
void command(int id, int fd, char *parameter) {
  char buffer[BUFFER_SIZE] = {0}, *command=NULL, *svalue=NULL;
  int i=0, res=0, ivalue=0, len=0;

  DBG("parameter is: %s\n", parameter);

  /* sanity check of parameter-string */
  if ( parameter == NULL || strlen(parameter) >= 100 || strlen(parameter) == 0 ) {
    DBG("parameter string looks bad\n");
    send_error(fd, 400, "Parameter-string of command does not look valid.");
    return;
  }

  /* search for required variable "command" */
  if ( (command = strstr(parameter, "command=")) == NULL ) {
    DBG("no command specified\n");
    send_error(fd, 400, "no GET variable \"command=...\" found, it is required to specify which command to execute");
    return;
  }

  /* allocate and copy command string */
  command += strlen("command=");
  len = strspn(command, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_1234567890");
  if ( (command = strndup(command, len)) == NULL ) {
    send_error(fd, 500, "could not allocate memory");
    LOG("could not allocate memory\n");
    return;
  }
  DBG("command string: %s\n", command);

  /* find and convert optional parameter "value" */
  if ( (svalue = strstr(parameter, "value=")) != NULL ) {
    svalue += strlen("value=");
    len = strspn(svalue, "-1234567890");
    if ( (svalue = strndup(svalue, len)) == NULL ) {
      if (command != NULL) free(command);
      send_error(fd, 500, "could not allocate memory");
      LOG("could not allocate memory\n");
      return;
    }
    ivalue = MAX(MIN(strtol(svalue, NULL, 10), 999), -999);
    DBG("converted value form string %s to integer %d\n", svalue, ivalue);
    free(svalue);
  }

  /*
   * determine command, try the input command-mappings first
   * this is the interface to send commands to the input plugin.
   * if the input-plugin does not implement the optional command
   * function, a short error is reported to the HTTP-client.
   */
  for ( i=0; i < LENGTH_OF(in_cmd_mapping); i++ ) {
    if ( strcmp(in_cmd_mapping[i].string, command) == 0 ) {

      if ( pglobal->in.cmd == NULL ) {
        send_error(fd, 501, "input plugin does not implement commands");
        if (command != NULL) free(command);
        return;
      }

      res = pglobal->in.cmd(in_cmd_mapping[i].cmd, ivalue);
      break;
    }
  }

  /* check if the command is for the output plugin itself */
  for ( i=0; i < LENGTH_OF(out_cmd_mapping); i++ ) {
    if ( strcmp(out_cmd_mapping[i].string, command) == 0 ) {
      res = output_cmd(id, out_cmd_mapping[i].cmd, ivalue);
      break;
    }
  }

  /* Send HTTP-response */
  sprintf(buffer, "HTTP/1.0 200 OK\r\n" \
                  "Content-type: text/plain\r\n" \
                  STD_HEADER \
                  "\r\n" \
                  "%s: %d", command, res);

  write(fd, buffer, strlen(buffer));

  if (command != NULL) free(command);
}

/******************************************************************************
Description.: Serve a connected TCP-client. This thread function is called
              for each connect of a HTTP client like a webbrowser. It determines
              if it is a valid HTTP request and dispatches between the different
              response options.
Input Value.: arg is the filedescriptor and server-context of the connected TCP
              socket. It must have been allocated so it is freeable by this
              thread function.
Return Value: always NULL
******************************************************************************/
/* thread for clients that connected to this server */
void *client_thread( void *arg )
{
	int cnt;
	char buffer[BUFFER_SIZE]={0}, *pb=buffer;
	iobuffer iobuf;
	request req;
	cfd lcfd; /* local-connected-file-descriptor */

	/* we really need the fildescriptor and it must be freeable by us */
	if (arg != NULL)	// 如果我们传人的参数不为空,则将参数的内容拷贝到 lcfd 中(参数为 pcfd ,不为空)
	{
		memcpy(&lcfd, arg, sizeof(cfd));
		free(arg);
	}
	else
		return NULL;

	/* initializes the structures */
	/* 初始化iobuf、req这两个变量 */
	init_iobuffer(&iobuf);		// 把iobuf清为0
	init_request(&req);		// http协议,需要客服端给服务器发送一个请求,而request就是这个请求

	/* What does the client want to receive? Read the request. */
	/* 从客服端接收一些数据,用来表示客服端发来的请求，才知道给客服端发什么数据 */
	memset(buffer, 0, sizeof(buffer));
	/* _readline:从客服端中读取一行的数据,以换行符结束 */
	/* buffer中存有"abcd\n" */
	/*
		客服此时必须发送一个请求字符串，以换行符作为结束!
		问:可以发送哪些字符串?
		答:有
			"GET /?action=snapshot\n"
			"GET /?action=stream\n"
			"GET /?action=command\n"
	*/
	if ( (cnt = _readline(lcfd.fd, &iobuf, buffer, sizeof(buffer)-1, 5)) == -1 )
	{
		close(lcfd.fd);
		return NULL;
	}

	/* determine what to deliver */
	/* 解析buf中的字符串 */
	if ( strstr(buffer, "GET /?action=snapshot") != NULL )
	{
		req.type = A_SNAPSHOT;	// 如果请求字符串中含有"GET /?action=snapshot"，则请求类型为 A_SNAPSHOT
	}
	else if ( strstr(buffer, "GET /?action=stream") != NULL )
	{
		req.type = A_STREAM;	// 如果请求字符串中含有"GET /?action=stream",则请求类型为 A_STREAM
	}
	else if ( strstr(buffer, "GET /?action=command") != NULL )
	{
		/* 将请求后面的参数保存到 req.parameter*/

		int len;
		req.type = A_COMMAND;

		/* advance by the length of known string */
		if ( (pb = strstr(buffer, "GET /?action=command")) == NULL ) {
		DBG("HTTP request seems to be malformed\n");
		send_error(lcfd.fd, 400, "Malformed HTTP request");
		close(lcfd.fd);
		return NULL;
		}
		pb += strlen("GET /?action=command");

		/* only accept certain characters */
		len = MIN(MAX(strspn(pb, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ_-=&1234567890"), 0), 100);
		req.parameter = malloc(len+1);
		if ( req.parameter == NULL ) {
		exit(EXIT_FAILURE);
		}
		memset(req.parameter, 0, len+1);
		strncpy(req.parameter, pb, len);

		DBG("command parameter (len: %d): \"%s\"\n", len, req.parameter);
	}
	else
	{	
		/* 将请求后面的参数保存到 req.parameter */
		int len;

		DBG("try to serve a file\n");
		req.type = A_FILE;

		if ( (pb = strstr(buffer, "GET /")) == NULL ) {
		DBG("HTTP request seems to be malformed\n");
		send_error(lcfd.fd, 400, "Malformed HTTP request");
		close(lcfd.fd);
		return NULL;
		}

		pb += strlen("GET /");
		len = MIN(MAX(strspn(pb, "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ._-1234567890"), 0), 100);
		req.parameter = malloc(len+1);
		if ( req.parameter == NULL ) {
		exit(EXIT_FAILURE);
		}
		memset(req.parameter, 0, len+1);
		strncpy(req.parameter, pb, len);

		DBG("parameter (len: %d): \"%s\"\n", len, req.parameter);
	}

	/*
	* parse the rest of the HTTP-request
	* the end of the request-header is marked by a single, empty line with "\r\n"
	*/
	do
	{
		memset(buffer, 0, sizeof(buffer));		// 将buffer清0

		/* 从客服端读取一行数据 */
		/*
			客服端必须再发送一次字符串
		*/
		if ( (cnt = _readline(lcfd.fd, &iobuf, buffer, sizeof(buffer)-1, 5)) == -1 )
		{
			free_request(&req);
			close(lcfd.fd);
			return NULL;
		}

		/* 开始解析 buffer 中的数据 */
		if ( strstr(buffer, "User-Agent: ") != NULL )
		{
			/* 如果buffer(客服端)中存有(发送了)用户名,则将用户名保存到 req.client 中 */
			req.client = strdup(buffer+strlen("User-Agent: "));
		}
		else if ( strstr(buffer, "Authorization: Basic ") != NULL )
		{
			/* 如果buffer(客服端)中存有(发送了)密码,则将密码保存到 req.credentials 中 */
			req.credentials = strdup(buffer+strlen("Authorization: Basic "));
			decodeBase64(req.credentials);		// 对密码进行解码
			DBG("username:password: %s\n", req.credentials);
		}

	} while( cnt > 2 && !(buffer[0] == '\r' && buffer[1] == '\n') );

	/* check for username and password if parameter -c was given */
	/* 如果支持密码功能,则要检查用户名和密码是否匹配 */
	if ( lcfd.pc->conf.credentials != NULL )
	{
		if ( req.credentials == NULL || strcmp(lcfd.pc->conf.credentials, req.credentials) != 0 ) {
		DBG("access denied\n");
		send_error(lcfd.fd, 401, "username and password do not match to configuration");
		close(lcfd.fd);
		if ( req.parameter != NULL ) free(req.parameter);
		if ( req.client != NULL ) free(req.client);
		if ( req.credentials != NULL ) free(req.credentials);
		return NULL;
		}
		DBG("access granted\n");
	}

	/* now it's time to answer */
	/* 根据请求的类型，采取相应的行动 */
	switch ( req.type )
	{
		case A_SNAPSHOT:
			DBG("Request for snapshot\n");
			send_snapshot(lcfd.fd);
			break;
		case A_STREAM:
			DBG("Request for stream\n");
			send_stream(lcfd.fd);
			break;
		case A_COMMAND:
			if ( lcfd.pc->conf.nocommands ) {
			send_error(lcfd.fd, 501, "this server is configured to not accept commands");
			break;
			}
			command(lcfd.pc->id, lcfd.fd, req.parameter);
			break;
		case A_FILE:
			if ( lcfd.pc->conf.www_folder == NULL )
			send_error(lcfd.fd, 501, "no www-folder configured");
			else
			send_file(lcfd.pc->id, lcfd.fd, req.parameter);
			break;
		default:
			DBG("unknown request\n");
	}

	close(lcfd.fd);
	free_request(&req);

	DBG("leaving HTTP client thread\n");
	return NULL;
}

/******************************************************************************
Description.: This function cleans up ressources allocated by the server_thread
Input Value.: arg is not used
Return Value: -
******************************************************************************/
void server_cleanup(void *arg) {
  context *pcontext = arg;

  OPRINT("cleaning up ressources allocated by server thread #%02d\n", pcontext->id);

  close(pcontext->sd);
}

/******************************************************************************
Description.: Open a TCP socket and wait for clients to connect. If clients
              connect, start a new thread for each accepted connection.
Input Value.: arg is a pointer to the globals struct
Return Value: always NULL, will only return on exit
******************************************************************************/
void *server_thread( void *arg )
{
	struct sockaddr_in addr, client_addr;
	int on;
	pthread_t client;
	socklen_t addr_len = sizeof(struct sockaddr_in);

	context *pcontext = arg;
	pglobal = pcontext->pglobal;		// 取出globals结构体变量

	/* set cleanup handler to cleanup ressources */
	/* 当线程结束的时候，会调用 server_cleanup 来做些清理工作 */
	pthread_cleanup_push(server_cleanup, pcontext);

	/* open socket for server */
	pcontext->sd = socket(PF_INET, SOCK_STREAM, 0);		// open函数
	if ( pcontext->sd < 0 )
	{
		fprintf(stderr, "socket failed\n");
		exit(EXIT_FAILURE);
	}

	/* ignore "socket already in use" errors */
	/*
		设置套接字
		SO_REUSEADDR:可以重复使用同一个IP和端口号
	*/
	on = 1;
	if (setsockopt(pcontext->sd, SOL_SOCKET, SO_REUSEADDR, &on, sizeof(on)) < 0)
	{
		perror("setsockopt(SO_REUSEADDR) failed");
		exit(EXIT_FAILURE);
	}

	/* perhaps we will use this keep-alive feature oneday */
	/* setsockopt(sd, SOL_SOCKET, SO_KEEPALIVE, &on, sizeof(on)); */

	/* configure server address to listen to all local IPs */
	memset(&addr, 0, sizeof(addr));
	addr.sin_family = AF_INET;
	addr.sin_port = pcontext->conf.port; /* (端口号)is already in right byteorder */
	addr.sin_addr.s_addr = htonl(INADDR_ANY);	/* 可以监听本地的所有的ip */
	if ( bind(pcontext->sd, (struct sockaddr*)&addr, sizeof(addr)) != 0 )	// 绑定端口和IP
	{
		perror("bind");
		OPRINT("%s(): bind(%d) failed", __FUNCTION__, htons(pcontext->conf.port));
		closelog();
		exit(EXIT_FAILURE);
	}

	/* start listening on socket */
	/* 启动监测数据,最多可以同时连接10个客服端 */
	if ( listen(pcontext->sd, 10) != 0 )
	{
		fprintf(stderr, "listen failed\n");
		exit(EXIT_FAILURE);
	}

	/* create a child for every client that connects */
	while ( !pglobal->stop )
	{
		//int *pfd = (int *)malloc(sizeof(int));
		cfd *pcfd = malloc(sizeof(cfd));		// 分配一个cfd结构体

		if (pcfd == NULL)		// 判断是否分配成功
		{
			fprintf(stderr, "failed to allocate (a very small amount of) memory\n");
			exit(EXIT_FAILURE);
		}

		DBG("waiting for clients to connect\n");
		/* 等待客服端的链接，如果有链接,则建立链接 */
		pcfd->fd = accept(pcontext->sd, (struct sockaddr *)&client_addr, &addr_len);
		pcfd->pc = pcontext;

		/* start new thread that will handle this TCP connected client */
		DBG("create thread to handle client that just established a connection\n");
		syslog(LOG_INFO, "serving client: %s:%d\n", inet_ntoa(client_addr.sin_addr), ntohs(client_addr.sin_port));	// 将调试信息写入记录本

		/* 创建一个线程 */
		/*
			pthread_create:
				第一个参数:线程标识符的指针
				第二个参数:设置线程的属性
				第三个参数:线程函数的起始地址
				第四个参数:传给线程函数的参数
		*/
		if( pthread_create(&client, NULL, &client_thread, pcfd) != 0 )
		{
			DBG("could not launch another client thread\n");
			close(pcfd->fd);
			free(pcfd);
			continue;
		}
		pthread_detach(client);	// 等待线程结束,回收资源
	}

	DBG("leaving server thread, calling cleanup function now\n");
	pthread_cleanup_pop(1);

	return NULL;
}















