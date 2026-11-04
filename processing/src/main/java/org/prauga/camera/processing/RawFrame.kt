/*
 * SPDX-FileCopyrightText: Saalim Quadri <danascape@gmail.com>
 * SPDX-License-Identifier: Apache-2.0
 */

package org.prauga.camera.processing

import java.nio.ShortBuffer

class RawFrame(val data: ShortBuffer, val rowStride: Int, val width: Int, val height: Int)
