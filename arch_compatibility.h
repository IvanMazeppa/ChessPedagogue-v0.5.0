// arch_compatibility.h
#ifndef ARCH_COMPATIBILITY_H
#define ARCH_COMPATIBILITY_H

// ARM-specific definitions to replace Intel intrinsics
#ifdef ARCH_ARM
#include <arm_neon.h>

// Define any compatibility macros here if needed
// For example, to provide ARM equivalents of Intel intrinsics

// Disable Intel-specific headers by providing empty definitions
#define __SSE__
#define __SSE2__
#define __SSE3__
#define __SSSE3__

// Define empty structs for Intel types to prevent compilation errors
typedef struct { } __m64;
typedef struct { } __m128;
typedef struct { } __m128i;
typedef struct { } __m128d;

// Define empty functions for commonly used Intel intrinsics
// Add more as needed based on compilation errors
#define _mm_set1_epi8(x) ((void)x, (__m128i){})
#define _mm_set1_epi16(x) ((void)x, (__m128i){})
#define _mm_set1_epi32(x) ((void)x, (__m128i){})
#define _mm_setzero_si128() ((__m128i){})
// Add more as needed
#endif

#endif // ARCH_COMPATIBILITY_H