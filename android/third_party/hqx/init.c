/*
 * Copyright (C) 2010 Cameron Zemek ( grom@zeminvaders.net)
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 */

#include "common.h"
#include "hqx.h"

/* FBA Arcade (2026): preserve the mapping, but replace the 64 MiB eager table
 * with a 128 KiB per-thread cache. Convert lazily on an exact 24-bit miss. */
static _Thread_local HqxColor colors[16384];

HqxColor *hqx_color_cache(void) { return colors; }

uint32_t hqx_convert_yuv(uint32_t c)
{
    int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
    unsigned y = (unsigned)(0.299*r + 0.587*g + 0.114*b);
    unsigned u = (unsigned)((int)(-0.169*r - 0.331*g + 0.5*b) + 128);
    unsigned v = (unsigned)((int)(0.5*r - 0.419*g - 0.081*b) + 128);
    return (y << 16) + (u << 8) + v;
}

HQX_API void HQX_CALLCONV hqxInit(void) {}
