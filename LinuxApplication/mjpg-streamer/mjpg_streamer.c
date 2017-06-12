/*******************************************************************************
#                                                                              #
#      MJPG-streamer allows to stream JPG frames from an input-plugin          #
#      to several output plugins                                               #
#                                                                              #
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

#include <stdio.h>
#include <stdlib.h>
#include <unistd.h>
#include <string.h>
#include <linux/videodev.h>
#include <sys/ioctl.h>
#include <errno.h>
#include <signal.h>
#include <sys/socket.h>
#include <arpa/inet.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <getopt.h>
#include <pthread.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <syslog.h>

#include "utils.h"
#include "mjpg_streamer.h"

/* globals */
static globals global;

/******************************************************************************
Description.: Display a help message
Input Value.: argv[0] is the program name and the parameter progname
Return Value: -
******************************************************************************/
void help(char *progname)
{
  fprintf(stderr, "-----------------------------------------------------------------------\n");
  fprintf(stderr, "Usage: %s\n" \
                  "  -i | --input \"<input-plugin.so> [parameters]\"\n" \
                  "  -o | --output \"<output-plugin.so> [parameters]\"\n" \
                  " [-h | --help ]........: display this help\n" \
                  " [-v | --version ].....: display version information\n" \
                  " [-b | --background]...: fork to the background, daemon mode\n", progname);
  fprintf(stderr, "-----------------------------------------------------------------------\n");
  fprintf(stderr, "Example #1:\n" \
                  " To open an UVC webcam \"/dev/video1\" and stream it via HTTP:\n" \
                  "  %s -i \"input_uvc.so -d /dev/video1\" -o \"output_http.so\"\n", progname);
  fprintf(stderr, "-----------------------------------------------------------------------\n");
  fprintf(stderr, "Example #2:\n" \
                  " To open an UVC webcam and stream via HTTP port 8090:\n" \
                  "  %s -i \"input_uvc.so\" -o \"output_http.so -p 8090\"\n", progname);
  fprintf(stderr, "-----------------------------------------------------------------------\n");
  fprintf(stderr, "Example #3:\n" \
                  " To get help for a certain input plugin:\n" \
                  "  %s -i \"input_uvc.so --help\"\n", progname);
  fprintf(stderr, "-----------------------------------------------------------------------\n");
  fprintf(stderr, "In case the modules (=plugins) can not be found:\n" \
                  " * Set the default search path for the modules with:\n" \
                  "   export LD_LIBRARY_PATH=/path/to/plugins,\n" \
                  " * or put the plugins into the \"/lib/\" or \"/usr/lib\" folder,\n" \
                  " * or instead of just providing the plugin file name, use a complete\n" \
                  "   path and filename:\n" \
                  "   %s -i \"/path/to/modules/input_uvc.so\"\n", progname);
  fprintf(stderr, "-----------------------------------------------------------------------\n");
}

/******************************************************************************
Description.: pressing CTRL+C sends signals to this process instead of just
              killing it plugins can tidily shutdown and free allocated
              ressources. The function prototype is defined by the system,
              because it is a callback function.
Input Value.: sig tells us which signal was received
Return Value: -
******************************************************************************/
void signal_handler(int sig)
{
  int i;

  /* signal "stop" to threads */
  LOG("setting signal to stop\n");
  global.stop = 1;
  usleep(1000*1000);

  /* clean up threads */
  LOG("force cancelation of threads and cleanup ressources\n");
  global.in.stop();
  for(i=0; i<global.outcnt; i++) {
    global.out[i].stop(global.out[i].param.id);
  }
  usleep(1000*1000);

  /* close handles of input plugins */
  dlclose(&global.in.handle);
  for(i=0; i<global.outcnt; i++) {
    /* skip = 0;
    DBG("about to decrement usage counter for handle of %s, id #%02d, handle: %p\n", \
        global.out[i].plugin, global.out[i].param.id, global.out[i].handle);
    for(j=i+1; j<global.outcnt; j++) {
      if ( global.out[i].handle == global.out[j].handle ) {
        DBG("handles are pointing to the same destination (%p == %p)\n", global.out[i].handle, global.out[j].handle);
        skip = 1;
      }
    }
    if ( skip ) {
      continue;
    }

    DBG("closing handle %p\n", global.out[i].handle);
    */
    dlclose(global.out[i].handle);
  }
  DBG("all plugin handles closed\n");

  pthread_cond_destroy(&global.db_update);
  pthread_mutex_destroy(&global.db);

  LOG("done\n");

  closelog();
  exit(0);
  return;
}

/******************************************************************************
Description.: 
Input Value.: 
Return Value: 
******************************************************************************/
int main(int argc, char *argv[])
{
	char *input  = "input_uvc.so --resolution 640x480 --fps 5 --device /dev/video0";	// 指向输入选项的参数字符串
	char *output[MAX_OUTPUT_PLUGINS];	/* 指向输出选项的参数字符串 */
	int daemon=0, i;						/* 是否让程序在后台运行的标志 */
	size_t tmp=0;

	output[0] = "output_http.so --port 8080";
	global.outcnt = 0;							/* 此时输出通道有几种方式, 0 */

	/* parameter parsing */
	/* 解析参数(关键是看懂 getopt_long_only 函数) */
	/*
	getopt_long_only():
		用于解析命令行选项
	参数解析:
		第二个参数:直接从main函数传递而来,表示我们传人的参数
		第三个参数:短选项字符串
		第四个参数:是 struct option 数组,用于存放长选项参数
		第五个参数:用于返回长选项在longopts结构体数组中的索引值
	返回值:
		解析完毕，返回-1
		出现未定义的长选项或者短选项，getopt_long返回？

	注意:
		长选项必须加'-'
	*/
	while(1)
	{
		int option_index = 0, c=0;
		static struct option long_options[] = \
		{
			{"h", no_argument, 0, 0},
			{"help", no_argument, 0, 0},
			{"i", required_argument, 0, 0},
			{"input", required_argument, 0, 0},
			{"o", required_argument, 0, 0},
			{"output", required_argument, 0, 0},
			{"v", no_argument, 0, 0},
			{"version", no_argument, 0, 0},
			{"b", no_argument, 0, 0},
			{"background", no_argument, 0, 0},
			{0, 0, 0, 0}
		};

		/* argv = " -i "input_uvc.so -f 10 -r 320*240" -o "output_http.so -w www" " */
		c = getopt_long_only(argc, argv, "", long_options, &option_index);

		/* no more options to parse */
		/* 参数解析完成 */
		if (c == -1)
			break;

		/* unrecognized option */
		/* 如果传人的参数不正确,则打印帮助信息 */
		if(c=='?')
		{
			help(argv[0]);
			return 0;
		}

		switch (option_index)
		{
			/* h, help */
			case 0:
			case 1:
			help(argv[0]);
			return 0;
			break;

			/* i, input */
			case 2:
			case 3:
				input = strdup(optarg);	// input = "input_uvc.so -f 10 -r 320*240"
				break;

			/* o, output */
			case 4:
			case 5:
				output[global.outcnt++] = strdup(optarg);	// output[0] = "output_http.so -w www"
				break;

			/* v, version */
			case 6:
			case 7:
			printf("MJPG Streamer Version: %s\n" \
			"Compilation Date.....: %s\n" \
			"Compilation Time.....: %s\n", SOURCE_VERSION, __DATE__, __TIME__);
			return 0;
			break;

			/* b, background */
			case 8:
			case 9:
			daemon=1;
			break;

			default:
			help(argv[0]);
			return 0;
		}
	}

	/* 打开一个程序的系统记录器的链接 */
	openlog("MJPG-streamer ", LOG_PID|LOG_CONS, LOG_USER);
	//openlog("MJPG-streamer ", LOG_PID|LOG_CONS|LOG_PERROR, LOG_USER);
	syslog(LOG_INFO, "starting application");		// 将 "starting application" 字符串写到系统记录中

	/* fork to the background */
	/* 如果daemon = 1,则让程序在后台运行 */
	if ( daemon )
	{
		LOG("enabling daemon mode");
		daemon_mode();
	}

	/* initialise the global variables */
	/* 初始化 global 中的成员 */
	global.stop      = 0;
	global.buf       = NULL;
	global.size      = 0;
	global.in.plugin = NULL;

	/* this mutex and the conditional variable are used to synchronize access to the global picture buffer */
	if( pthread_mutex_init(&global.db, NULL) != 0 )		/* 初始化 global.db 成员 */
	{
		LOG("could not initialize mutex variable\n");
		closelog();
		exit(EXIT_FAILURE);
	}
	if( pthread_cond_init(&global.db_update, NULL) != 0 )	/* 初始化 global.db_update(条件变量) 成员 */
	{
		LOG("could not initialize condition variable\n");
		closelog();
		exit(EXIT_FAILURE);
	}

	/* ignore SIGPIPE (send by OS if transmitting to closed TCP sockets) */
	signal(SIGPIPE, SIG_IGN);		/* 忽略 SIGPIPE 信号 */

	/* register signal handler for <CTRL>+C in order to clean up */
	/*
		当我们按下 <CTRL>+C 时,则调用signal_handler()函数,做一些清理工作
	*/
	if (signal(SIGINT, signal_handler) == SIG_ERR)
	{
		LOG("could not register signal handler\n");
		closelog();
		exit(EXIT_FAILURE);
	}

	/*
	* messages like the following will only be visible on your terminal
	* if not running in daemon mode
	*/
	LOG("MJPG Streamer Version.: %s\n", SOURCE_VERSION);		// 打印出 MJPG Streamer 版本号

	/* check if at least one output plugin was selected */
	if ( global.outcnt == 0 )	// 如果输出方式的种类为0,则让他为1
	{
		/* no? Then use the default plugin instead */
		global.outcnt = 1;
	}

	/* open input plugin */
	/* "input_uvc.so -f 10 -r 320*240" */
	tmp = (size_t)(strchr(input, ' ')-input);		// 让tmp 等于 "input_uvc.so"字符串的长度
	global.in.plugin = (tmp > 0)?strndup(input, tmp):strdup(input);	// global.in.plugin = "input_uvc.so"
	global.in.handle = dlopen(global.in.plugin, RTLD_LAZY);			// 打开 "input_uvc.so" 这个动态链接库
	if ( !global.in.handle )
	{
		LOG("ERROR: could not find input plugin\n");
		LOG("       Perhaps you want to adjust the search path with:\n");
		LOG("       # export LD_LIBRARY_PATH=/path/to/plugin/folder\n");
		LOG("       dlopen: %s\n", dlerror() );
		closelog();
		exit(EXIT_FAILURE);
	}
	global.in.init = dlsym(global.in.handle, "input_init");	// 让 global.in.init = input_init
	if ( global.in.init == NULL )
	{
		LOG("%s\n", dlerror());
		exit(EXIT_FAILURE);
	}
	global.in.stop = dlsym(global.in.handle, "input_stop");	// 让 global.in.stop = input_stop
	if ( global.in.stop == NULL )
	{
		LOG("%s\n", dlerror());
		exit(EXIT_FAILURE);
	}
	global.in.run = dlsym(global.in.handle, "input_run");	// 让 global.in.run = input_run
	if ( global.in.run == NULL )
	{
		LOG("%s\n", dlerror());
		exit(EXIT_FAILURE);
	}
	/* try to find optional command */
	global.in.cmd = dlsym(global.in.handle, "input_cmd");	// global.in.cmd = input_cmd

	global.in.param.parameter_string = strchr(input, ' ');		// global.in.param.parameter_string = "-f 10 -r 320*240"
	global.in.param.global = &global;

	if ( global.in.init(&global.in.param) )		// 调用input_uvc.c中的input_init(&global.in.param)函数
	{
		LOG("input_init() return value signals to exit");
		closelog();
		exit(0);
	}

	/* open output plugin */
	for (i=0; i<global.outcnt; i++)		// outcnt = 1
	{
		/* "output_http.so -w www" */
		tmp = (size_t)(strchr(output[i], ' ')-output[i]);		// 让tmp 等于 "output_http.so"字符串的长度
		global.out[i].plugin = (tmp > 0)?strndup(output[i], tmp):strdup(output[i]);	// 让 global.out[i].plugin = "output_http.so"
		global.out[i].handle = dlopen(global.out[i].plugin, RTLD_LAZY);	// 打开 "output_http.so" 动态链接库
		if ( !global.out[i].handle )
		{
			LOG("ERROR: could not find output plugin %s\n", global.out[i].plugin);
			LOG("       Perhaps you want to adjust the search path with:\n");
			LOG("       # export LD_LIBRARY_PATH=/path/to/plugin/folder\n");
			LOG("       dlopen: %s\n", dlerror() );
			closelog();
			exit(EXIT_FAILURE);
		}
		global.out[i].init = dlsym(global.out[i].handle, "output_init");		// 让 global.out[i].init = output_init
		if ( global.out[i].init == NULL )
		{
			LOG("%s\n", dlerror());
			exit(EXIT_FAILURE);
		}
		global.out[i].stop = dlsym(global.out[i].handle, "output_stop");	// 让 global.out[i].stop = output_stop
		if ( global.out[i].stop == NULL )
		{
			LOG("%s\n", dlerror());
			exit(EXIT_FAILURE);
		}
		global.out[i].run = dlsym(global.out[i].handle, "output_run");		// 让 global.out[i].run = output_run
		if ( global.out[i].run == NULL )
		{
			LOG("%s\n", dlerror());
			exit(EXIT_FAILURE);
		}
		/* try to find optional command */
		global.out[i].cmd = dlsym(global.out[i].handle, "output_cmd");	// 让 global.out[i].cmd = output_cmd

		global.out[i].param.parameter_string = strchr(output[i], ' ');	// 让 global.out[i].param.parameter_string = "-w www"
		global.out[i].param.global = &global;
		global.out[i].param.id = i;
		if ( global.out[i].init(&global.out[i].param) )	// 调用 output_http.c中的output_init(&global.out[i].param)函数
		{
			LOG("output_init() return value signals to exit");
			closelog();
			exit(0);
		}
	}

	/* start to read the input, push pictures into global buffer */
	DBG("starting input plugin\n");			// 打印调试信息 "starting input plugin\n"
	syslog(LOG_INFO, "starting input plugin");	// 将 "starting input plugin" 字符串写到记录本中
	global.in.run();		// 调用 input_uvc.c中的input_run函数

	DBG("starting %d output plugin(s)\n", global.outcnt);	// 打印调试信息
	for(i=0; i<global.outcnt; i++)
	{
		syslog(LOG_INFO, "starting output plugin: %s (ID: %02d)", global.out[i].plugin, global.out[i].param.id);
		global.out[i].run(global.out[i].param.id);		// 调用 output_http.c 中的 output_run 函数
	}

	/* wait for signals */
	pause();			// 程序等待信号的发生

	return 0;
}
