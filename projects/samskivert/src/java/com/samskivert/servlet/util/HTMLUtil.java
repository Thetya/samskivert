//
// $Id: HTMLUtil.java,v 1.2 2002/12/30 04:52:36 mdb Exp $
//
// samskivert library - useful routines for java programs
// Copyright (C) 2001 Michael Bayne
// 
// This library is free software; you can redistribute it and/or modify it
// under the terms of the GNU Lesser General Public License as published
// by the Free Software Foundation; either version 2.1 of the License, or
// (at your option) any later version.
//
// This library is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
// Lesser General Public License for more details.
//
// You should have received a copy of the GNU Lesser General Public
// License along with this library; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA

package com.samskivert.servlet.util;

import com.samskivert.util.StringUtil;

/**
 * HTML related utility functions.
 */
public class HTMLUtil
{
    /**
     * Converts instances of <code><, >, & and "</code> into their
     * entified equivalents: <code>&lt;, &gt;, &amp; and &quot;</code>.
     * These characters are mentioned in the HTML spec as being common
     * candidates for entification.
     *
     * @return the entified string.
     */
    public static String entify (String text)
    {
        // this could perhaps be done more efficiently, but this function
        // is not likely to be called on large quantities of text
        text = StringUtil.replace(text, "&", "&amp;");
        text = StringUtil.replace(text, "<", "&lt;");
        text = StringUtil.replace(text, ">", "&gt;");
        return StringUtil.replace(text, "\"", "&quot;");
    }

    /**
     * Inserts a &lt;p&gt; tag between every two consecutive newlines.
     */
    public static String makeParagraphs (String text)
    {
        if (text == null) {
            return text;
        }
        // handle both line ending formats
        text = StringUtil.replace(text, "\n\n", "\n<p>\n");
        text = StringUtil.replace(text, "\r\n\r\n", "\r\n<p>\r\n");
        return text;
    }
}
