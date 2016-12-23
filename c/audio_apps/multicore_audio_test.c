#include "libaudio/audio.h"
#include "libaudio/audio.c"

const int LIM = 1000;
//master core
const int NOC_MASTER = 0;


void threadFunc(void* args) {
    volatile _UNCACHED int **inArgs = (volatile _UNCACHED int **) args;
    volatile _UNCACHED int *exitP      = inArgs[0];
    volatile _UNCACHED int *allocsDoneP = inArgs[1];


    /*
      AUDIO STUFF HERE
    */

    int cpuid = get_cpuid();


    int FX_HERE[MODES]; //amount of effects in this core

    //create structs
    struct AudioFX FXp[MODES][MAX_FX]; //[MODES][FX_HERE]


    for(int mode=0; mode<MODES; mode++) {

        // -------------------ALLOCATE FX------------------//
        FX_HERE[mode] = 0;

        int *FX_SCHED = (int *)FX_SCHED_PNT[mode];

        for(int n=0; n<FX_AMOUNT[mode]; n++) {
            if(*(FX_SCHED+n*10+1) == cpuid) {
                FX_HERE[mode]++;
            }
        }
        //struct parameters:
        int fx_id;
        fx_t fx_type;
        int xb_size, yb_size, p;
        con_t in_con;
        con_t out_con;
        fst_t is_fst;
        lst_t is_lst;

        // READ FROM SCHEDULER
        int fx_ind = 0;
        for(int n=0; n<FX_AMOUNT[mode]; n++) {
            if(*(FX_SCHED+n*10+1) == cpuid) { //same core
                //assign parameters from SCHEDULER
                fx_id   =         *(FX_SCHED+n*10+0);
                fx_type = (fx_t)  *(FX_SCHED+n*10+2);
                xb_size =         *(FX_SCHED+n*10+3);
                yb_size =         *(FX_SCHED+n*10+4);
                p       =         *(FX_SCHED+n*10+5);
                in_con  = (con_t) *(FX_SCHED+n*10+6);
                out_con = (con_t) *(FX_SCHED+n*10+7);
                if(*(FX_SCHED+n*10+8) == -1) {
                    is_fst = FIRST;
                }
                else {
                    is_fst = NO_FIRST;
                }
                if(*(FX_SCHED+n*10+9) == -1) {
                    is_lst = LAST;
                }
                else {
                    is_lst = NO_LAST;
                }
                //allocate
                alloc_audio_vars(&FXp[mode][fx_ind], fx_id, fx_type, in_con,
                    out_con, xb_size, yb_size, p, is_fst, is_lst);
                fx_ind++;
            }
        }



        //CONNECT EFFECTS
        for(int n=0; n<FX_HERE[mode]; n++) {
            // same core
            if(*FXp[mode][n].out_con == NO_NOC) {
                //ID to connect to
                int destID = *(FX_SCHED + (*FXp[mode][n].fx_id)*10 + 9);
                if(*(FX_SCHED+destID*10+8) != *FXp[mode][n].fx_id) {
                    printf("ERROR: SAME CORE CONNECTION MISMATCH\n");
                }
                for(int m=0; m<FX_HERE[mode]; m++) {
                    if(*FXp[mode][m].fx_id == destID) {
                        audio_connect_same_core(&FXp[mode][n], &FXp[mode][m]);
                        break;
                    }
                }
            }
            // NoC
            if(*FXp[mode][n].in_con == NOC) {
                //NoC receive channel ID
                int recvChanID = *(FX_SCHED + (*FXp[mode][n].fx_id)*10 + 8);
                audio_connect_from_core(recvChanID, &FXp[mode][n]);
            }
            if(*FXp[mode][n].out_con == NOC) {
                //NoC send channel ID
                int sendChanID = *(FX_SCHED + (*FXp[mode][n].fx_id)*10 + 9);
                audio_connect_to_core(&FXp[mode][n], sendChanID);
            }
        }
    }

    // wait until all cores are ready
    allocsDoneP[cpuid] = 1;
    for(int i=0; i<ALL_CORES; i++) {
        while(allocsDoneP[i] == 0);
    }


    // Initialize the communication channels
    int nocret = mp_init_ports();


    volatile _UNCACHED unsigned int *current_modeP = (volatile _UNCACHED unsigned int *) &current_mode;

    //loop
    //audioValuesP[0] = 0;
    //int i=0;
    while(*exitP == 0) {
    //i++;
    //for(int i=0; i<DEBUG_LOOPLENGTH; i++) {

        for(int n=0; n<FX_HERE[*current_modeP]; n++) {
            if (audio_process(&FXp[*current_modeP][n]) == 1) {
                //timeout stuff here
            }
        }

    }

    //free memory allocation
    for(int mode=0; mode<MODES; mode++) {
        for(int n=0; n<FX_HERE[mode]; n++) {
            free_audio_vars(&FXp[mode][n]);
        }
    }


    // exit with return value
    int ret = 0;
    corethread_exit(&ret);
    return;
}



int main() {

    //arguments to thread 1 function
    int exit = 0;
    int allocsDone[ALL_CORES];
    for(int i=0; i<ALL_CORES; i++) {
        allocsDone[i] = 0;
    }
    volatile _UNCACHED int *exitP = (volatile _UNCACHED int *) &exit;
    volatile _UNCACHED int *allocsDoneP = (volatile _UNCACHED int *) &allocsDone;
    volatile _UNCACHED int (*threadFunc_args[1+ALL_CORES]);
    threadFunc_args[0] = exitP;
    threadFunc_args[1] = allocsDoneP;

    printf("starting thread and NoC channels...\n");
    //set thread function and start thread
    corethread_t threads[ALL_CORES-1];
    for(int i=0; i<(ALL_CORES-1); i++) {
        threads[i] = (corethread_t) (i+1);
        corethread_create(&threads[i], &threadFunc, (void*) threadFunc_args);
        printf("Thread created on core %d\n", i+1);
    }

    #if GUITAR == 1
    setup(1); //for guitar
    #else
    setup(0); //for volca
    #endif

    // enable input and output
    *audioDacEnReg = 1;
    *audioAdcEnReg = 1;

    setInputBufferSize(BUFFER_SIZE);
    setOutputBufferSize(BUFFER_SIZE);


    int cpuid = get_cpuid();


    int FX_HERE[MODES]; //amount of effects in this core

    //create structs
    struct AudioFX FXp[MODES][MAX_FX]; //[MODES][FX_HERE]

    for(int mode=0; mode<MODES; mode++) {

        printf("SETTING UP MODE %d\n", mode);

        // -------------------ALLOCATE FX------------------//
        FX_HERE[mode] = 0;

        int *FX_SCHED = (int *)FX_SCHED_PNT[mode];

        for(int n=0; n<FX_AMOUNT[mode]; n++) {
            if(*(FX_SCHED+n*10+1) == cpuid) {
                FX_HERE[mode]++;
            }
        }
        //struct parameters:
        int fx_id;
        fx_t fx_type;
        int xb_size, yb_size, p;
        con_t in_con;
        con_t out_con;
        fst_t is_fst;
        lst_t is_lst;

    	// READ FROM SCHEDULER
    	int fx_ind = 0;
    	for(int n=0; n<FX_AMOUNT[mode]; n++) {
            if(*(FX_SCHED+n*10+1) == cpuid) { //same core
                //assign parameters from SCHEDULER
                fx_id   =         *(FX_SCHED+n*10+0);
                fx_type = (fx_t)  *(FX_SCHED+n*10+2);
                xb_size =         *(FX_SCHED+n*10+3);
                yb_size =         *(FX_SCHED+n*10+4);
                p       =         *(FX_SCHED+n*10+5);
                in_con  = (con_t) *(FX_SCHED+n*10+6);
                out_con = (con_t) *(FX_SCHED+n*10+7);
                if(*(FX_SCHED+n*10+8) == -1) {
                    is_fst = FIRST;
                }
                else {
                    is_fst = NO_FIRST;
                }
                if(*(FX_SCHED+n*10+9) == -1) {
                    is_lst = LAST;
                }
                else {
                    is_lst = NO_LAST;
                }
                //allocate
                alloc_audio_vars(&FXp[mode][fx_ind], fx_id, fx_type, in_con,
                    out_con, xb_size, yb_size, p, is_fst, is_lst);
                fx_ind++;
            }
        }

        //CONNECT EFFECTS
        for(int n=0; n<FX_HERE[mode]; n++) {
            //print input effects
            if(*FXp[mode][n].is_fst == FIRST) {
                printf("FIRST: ID=%d\n", *FXp[mode][n].fx_id);
            }
            // same core
            if( (*FXp[mode][n].out_con == NO_NOC) && (*FXp[mode][n].is_lst == NO_LAST) ) {
                //ID to connect to
                int destID = *(FX_SCHED + (*FXp[mode][n].fx_id)*10 + 9);
                if(*(FX_SCHED+destID*10+8) != *FXp[mode][n].fx_id) {
                    printf("ERROR: SAME CORE CONNECTION MISMATCH\n");
                }
                for(int m=0; m<FX_HERE[mode]; m++) {
                    if(*FXp[mode][m].fx_id == destID) {
                        audio_connect_same_core(&FXp[mode][n], &FXp[mode][m]);
                        printf("SAME CORE: connected ID=%d and ID=%d\n", *FXp[mode][n].fx_id, *FXp[mode][m].fx_id);
                        break;
                    }
                }
            }
            // NoC
            if(*FXp[mode][n].in_con == NOC) {
                //NoC send channel ID
                int recvChanID = *(FX_SCHED + (*FXp[mode][n].fx_id)*10 + 8);
                audio_connect_from_core(recvChanID, &FXp[mode][n]);
                printf("NoC: connected recvChanelID=%d to ID=%d\n", recvChanID, *FXp[mode][n].fx_id);
            }
            if(*FXp[mode][n].out_con == NOC) {
                //NoC send channel ID
                int sendChanID = *(FX_SCHED + (*FXp[mode][n].fx_id)*10 + 9);
                audio_connect_to_core(&FXp[mode][n], sendChanID);
                printf("NoC: connected ID=%d to sendChanelID=%d\n", *FXp[mode][n].fx_id, sendChanID);
            }
            //print output effects
            if(*FXp[mode][n].is_lst == LAST) {
                printf("LAST: ID=%d\n", *FXp[mode][n].fx_id);
            }
        }
    }

    // wait until all cores are ready
    allocsDoneP[cpuid] = 1;
    for(int i=0; i<ALL_CORES; i++) {
        while(allocsDoneP[i] == 0);
        printf("allocs done for core %d\n", i);
    }


    // Initialize the communication channels
    int nocret = mp_init_ports();
    if(nocret == 1) {
        printf("Thread and NoC initialised correctly\n");
    }
    else {
        printf("ERROR: Problem with NoC initialisation\n");
    }

    /*
    printf("SOME INFO: \n");
    printf("FX_HERE=%d\n", FX_HERE);
    printf("fx_id: %d, cpuid: %d, is_fst:%u, is_lst: %u, in_con: %u, out_con: %u\n", *FXp[0].fx_id, *FXp[0].cpuid, *FXp[0].is_fst, *FXp[0].is_lst, *FXp[0].in_con, *FXp[0].out_con);
    printf("x_pnt=0x%x, y_pnt=0x%x, pt=%u, p=%u, rpr=%u, spr=%u, ppsr=%u, xb_size=%u, yb_size=%u, fx=%u, fx_pnt=0x%x\n", *FXp[0].x_pnt, *FXp[0].y_pnt, *FXp[0].pt, *FXp[0].p, *FXp[0].rpr, *FXp[0].spr, *FXp[0].ppsr, *FXp[0].xb_size, *FXp[0].yb_size, *FXp[0].fx, *FXp[0].fx_pnt);
    */


    //CPU cycles stuff
    int CPUcycles[LIM] = {0};
    unsigned int cpu_pnt = 0;


    //int wait_recv = 18; //amount of loops until audioOut is done
    //int wait_recv = LATENCY;
    //for debugging
    //const int WAIT = wait_recv;

    //short audio_in[LIM][2] = {0};
    //short audio_out[LIM][2] = {0};

    int keyPressed = 0;

    volatile _UNCACHED unsigned int *current_modeP = (volatile _UNCACHED unsigned int *) &current_mode;

    while(*keyReg != 3) {

        if(*keyReg == 14) {
            if(keyPressed == 0) {
                keyPressed = 1;
                *current_modeP = (*current_modeP+1)%MODES;
                printf("reconfiguration! current mode: %u\n", *current_modeP);
            }
        }
        else {
            keyPressed = 0;
        }

        for(int n=0; n<FX_HERE[*current_modeP]; n++) {
            audio_process(&FXp[*current_modeP][n]);
            /*
              if(n==0) {
              audio_in[cpu_pnt][0] = FXp[n].x[0];
              audio_in[cpu_pnt][1] = FXp[n].x[1];
              }
              if(n==(FX_HERE-1)) {
              audio_out[cpu_pnt-WAIT][0] = FXp[n].y[0];
              audio_out[cpu_pnt-WAIT][1] = FXp[n].y[1];
              }
            */
        }


        //store CPU Cycles
        CPUcycles[cpu_pnt] = get_cpu_cycles();
        cpu_pnt++;
        if(cpu_pnt == LIM) {
            //break;
            cpu_pnt = 0;
        }


    }

    //exit stuff
    printf("exit here!\n");
    exit = 1;
    printf("waiting for all threads to finish...\n");

    //free memory allocation
    for(int mode=0; mode<MODES; mode++) {
    	for(int n=0; n<FX_HERE[mode]; n++) {
        	free_audio_vars(&FXp[mode][n]);
    	}
    }

    /*
    for(int i=1; i<LIM; i++) {
        printf("%d\n", (CPUcycles[i]-CPUcycles[i-1]));
    }
    */

    /*
    for(int i=0; i<(LIM-WAIT); i++) {
        if( (audio_in[i][0] != audio_out[i][0]) || (audio_in[i][1] != audio_out[i][1]) ){
            printf("CORRUPT: i=%d: x[0]=%d, y[0]=%d   :   x[1]=%d, y[1]=%d\n", i, audio_in[i][0], audio_out[i][0], audio_in[i][1], audio_out[i][1]);
        }
    }
    */

    //join with thread 1

    int *retval;
    for(int i=0; i<(ALL_CORES-1); i++) {
        corethread_join(threads[i], (void **)&retval);
        printf("thread %d finished!\n", (i+1));
    }

    return 0;
}
