// This header file defines the API for interacting with a DSP Chain

#ifndef __{{chain}}_GUARD
#define __{{chain}}_GUARD

#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>

#include "memcpy.h"
{{#each blocks}}
{{#each addrs}}#define {{blockname}}_{{this.addrname}} {{addr}}
{{/each}}
{{/each}}
typedef struct sam_capture {
    unsigned long ctrl_base;
    unsigned long data_base;
    unsigned long io_width;
    unsigned long mem_width;
    unsigned long pow2_width;
    unsigned long n_samps;
    unsigned long start_addr;
    void *output;
    unsigned long output_size;
    unsigned long output_bytes_valid;
    bool wait_for_sync;
    bool check_writes;
    bool use_dma;
} sam_capture;
{{#each sam}}
#define {{samname}}_CTRL_BASE {{ctrl_base}}
#define {{samname}}_DATA_BASE {{data_base}}

static sam_capture {{samname}}_capture =
(sam_capture) {
    {{ctrl_base}}, // ctrl_base
    {{data_base}}, // data_base
    {{io_width}},
    {{mem_width}},
    {{pow2_width}},
    0L,            // n_samps
    0L,            // start_addr
    0L,            // output
    0L,            // output_size
    0L,            // output_bytes_valid
    0,             // wait_for_sync
    1,             // check_writes
    0,             // use_dma
};
{{/each}}
#define SAM_W_START_ADDR_OFFSET     {{samWStartAddrOffset}}
#define SAM_W_TARGET_COUNT_OFFSET   {{samWTargetCountOffset}}
#define SAM_W_TRIG_OFFSET           {{samWTrigOffset}}
#define SAM_W_WAIT_FOR_SYNC_OFFSET  {{samWWaitForSyncOffset}}

#define SAM_W_WRITE_COUNT_OFFSET    {{samWWriteCountOffset}}
#define SAM_W_PACKET_COUNT_OFFSET   {{samWPacketCountOffset}}
#define SAM_W_SYNC_ADDR_OFFSET      {{samWSyncAddrOffset}}
#define SAM_W_STATE_OFFSET          {{samWStateOffset}}

{{#each chain_scr}}
#define {{blockname}}_{{addrname}} {{addr}}{{/each}}

#define INITIATE_SAM_CAPTURE_ERROR (255)
#define NOT_IMPLEMENTED_ERROR      (254)

#ifdef CRAFT_DEBUG
#define write_reg_debug(addr, data, name) ({                      \
    typeof(addr) addr_ = (addr);                                  \
    typeof(data) data_ = (data);                                  \
    typeof(name) name_ = (name);                                  \
    printf("Setting %s = 0x%llx at 0x%llx\n", name_, data_, addr_); \
    write_reg(addr_, data_);                                      \
})
#else
#define write_reg_debug(addr, data, name) ({                      \
    write_reg( (addr), (data) );                                  \
})
#endif

#ifdef CRAFT_DEBUG
#define read_reg_debug(addr, name) ({                        \
    typeof(addr) addr_ = (addr);                             \
    typeof(name) name_ = (name);                             \
    unsigned long ret = read_reg(addr_);                     \
    printf("Read %s at 0x%llx = 0x%llx\n", name_, addr_, ret); \
    ret;                                                     \
})
#else
#define read_reg_debug(addr, name) ({                        \
    read_reg( (addr) );                                      \
})
#endif

static inline void write_reg(unsigned long addr, unsigned long data)
{
    volatile unsigned long *ptr = (volatile unsigned long *) addr;
    *ptr = data;
}

static inline unsigned long read_reg(unsigned long addr)
{
    volatile unsigned long *ptr = (volatile unsigned long *) addr;
    return *ptr;
}

static inline bool decoupledHelper(unsigned long enAddr, unsigned long finishAddr, const char* name) {
    bool ret;
#ifdef CRAFT_DEBUG
    printf("Running decoupled transaction for %s\n", name);
#endif
    write_reg_debug(enAddr, 1, "enable");
    ret = read_reg_debug(finishAddr, "finished") != 0;
    write_reg_debug(enAddr, 0, "enable");
    return ret;
}

static void initiate_sam_capture(sam_capture* cap)
{
    unsigned long base = cap->ctrl_base;

    write_reg_debug(base + SAM_W_START_ADDR_OFFSET,    cap->start_addr, "wStartAddr");
    write_reg_debug(base + SAM_W_TARGET_COUNT_OFFSET,  cap->n_samps, "wTargetCount");
    write_reg_debug(base + SAM_W_TRIG_OFFSET,          0L, "wTrig");
    write_reg_debug(base + SAM_W_WAIT_FOR_SYNC_OFFSET, cap->wait_for_sync, "wWaitForSync");
    write_reg_debug(base + SAM_W_TRIG_OFFSET,          1L, "wTrig");

    if (cap -> check_writes) {
        if (read_reg_debug(base + SAM_W_START_ADDR_OFFSET, "wStartAddr") !=
                cap->start_addr)
            exit(-INITIATE_SAM_CAPTURE_ERROR);
        if (read_reg_debug(base + SAM_W_TARGET_COUNT_OFFSET, "wTargetCount") !=
                cap->n_samps)
            exit(-INITIATE_SAM_CAPTURE_ERROR);
        if (read_reg_debug(base + SAM_W_TRIG_OFFSET, "wTrig") != 1L)
            exit(-INITIATE_SAM_CAPTURE_ERROR);
        if (read_reg_debug(base + SAM_W_WAIT_FOR_SYNC_OFFSET, "wWaitForSync") !=
                cap->wait_for_sync)
            exit(-INITIATE_SAM_CAPTURE_ERROR);
    }
}

static void get_sam_output(sam_capture* cap)
{
    unsigned long i;
    unsigned long ctrl_base = cap->ctrl_base;
    unsigned long data_base = cap->data_base;
    unsigned long *arr      = (unsigned long*)(cap->output);

    unsigned long write_count  =
        read_reg_debug(ctrl_base + SAM_W_WRITE_COUNT_OFFSET, "wWriteCount");
    unsigned long packet_count =
        read_reg_debug(ctrl_base + SAM_W_PACKET_COUNT_OFFSET, "wPacketCount");
    unsigned long sync_addr    =
        read_reg_debug(ctrl_base + SAM_W_SYNC_ADDR_OFFSET, "wSyncAddr");
    unsigned long state        =
        read_reg_debug(ctrl_base + SAM_W_STATE_OFFSET, "wState");

#ifdef CRAFT_DEBUG
    printf("Tuner CTRL base addr:0x%llx\n", ctrl_base);
    printf("Tuner DATA base addr:0x%llx\n", data_base);
    printf("Tuner write_count   :0x%llx\n", write_count);
    printf("Tuner packet_count  :0x%llx\n", packet_count);
    printf("Tuner sync_addr     :0x%llx\n", sync_addr);
    printf("Tuner state         :0x%llx\n", state);
#endif

    if (cap->use_dma) {
#ifdef CRAFT_DEBUG
        printf("Using DMA");
#endif
        test_memcpy(arr, (unsigned long*)data_base, write_count * 8 * sizeof(unsigned long));
        for (i=0L; i<write_count * 8; i++) {
            printf("arr[%d] = 0x%llx\n", i, arr[i]);
        }
    } else {
        for (i=0L; i<write_count * 8; i++) {
            arr[i] =
                read_reg_debug(data_base + i * 8, "data");
        }
    }
}

#endif
