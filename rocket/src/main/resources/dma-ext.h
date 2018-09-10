#ifndef __DMA_EXT_H__
#define __DMA_EXT_H__

#define SRC_STRIDE 0
#define DST_STRIDE 1
#define SEGMENT_SIZE 2
#define NSEGMENTS 3
#define ACCEL_CTRL 4
#define RESP_STATUS 5
#define RESP_VPN 6

#define NO_ERROR 0
#define PAUSED 1
#define SRC_PAGE_FAULT 2
#define DST_PAGE_FAULT 3
#define SRC_INVALID_REGION 4
#define DST_INVALID_REGION 5

#define CTRL_ALLOC_SRC 1
#define CTRL_ALLOC_DST 2
#define CTRL_ALLOC_PAUSE 4

static inline void dma_clear_cr(int regnum, unsigned long value)
{
	asm volatile ("custom2 0, %[regnum], %[value], 7" ::
			[regnum] "r" (regnum), [value] "r" (value));
}

static inline void dma_set_cr(int regnum, unsigned long value)
{
	asm volatile ("custom2 0, %[regnum], %[value], 6" ::
			[regnum] "r" (regnum), [value] "r" (value));
}

static inline void dma_write_cr(int regnum, unsigned long value)
{
	asm volatile ("custom2 0, %[regnum], %[value], 5" ::
			[regnum] "r" (regnum), [value] "r" (value));
}

static inline unsigned long dma_read_cr(int regnum)
{
	int value;
	asm volatile ("custom2 %[value], %[regnum], 0, 4" : 
			[value] "=r" (value) : [regnum] "r" (regnum));
	return value;
}

static inline void dma_transfer(void *dst, void *src)
{
	asm volatile ("custom2 0, %[dst], %[src], 0" ::
			[dst] "r" (dst), [src] "r" (src));
}

static inline void dma_read_prefetch(void *dst)
{
	asm volatile ("custom2 0, %[dst], 0, 2" :: [dst] "r" (dst));
}

static inline void dma_write_prefetch(void *dst)
{
	asm volatile ("custom2 0, %[dst], 0, 3" :: [dst] "r" (dst));
}

static inline void dma_resume(void)
{
	asm volatile ("custom2 0, 0, 0, 1");
}

#endif
