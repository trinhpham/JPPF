/*
 * Java Parallel Processing Framework.
 * Copyright (C) 2005-2006 Laurent Cohen.
 * lcohen@osp-chicago.com
 * 
 * This program is free software; you can redistribute it and/or modify it under the terms of
 * the GNU General Public License as published by the Free Software Foundation;
 * either version 2 of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with this program;
 * if not, write to the Free Software Foundation, Inc.,
 * 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307 USA
 */
package org.jppf.utils;


/**
 * This class provides a set of utility methods for manipulating strings. 
 * @author Laurent Cohen
 */
public final class StringUtils
{
	/**
	 * Instantiation of this class is not permitted.
	 */
	private StringUtils()
	{
	}

	/**
	 * Format a string so that it fits into a string of specified length.<br>
	 * If the string is longer than the specified length, then characters on the left are truncated, ortherwise
	 * the specified character is appended to the result on the left  to obtain the appropriate length.
	 * @param source the string to format; if null, it is considered an empty string.
	 * @param padChar the character used to fill the result up to the specified length.
	 * @param maxLen the length of the formatted string.
	 * @return a string formatted to the specified length.
	 */
	public static String padLeft(String source, char padChar, int maxLen)
	{
		StringBuilder sb = new StringBuilder();
		if (source == null) source = "";
		int length = source.length();
		if (length > maxLen) sb.append(source, length-maxLen, maxLen);
		else
		{
			for (int i=0; i<maxLen-length; i++) sb.append(padChar);
			sb.append(source);
		}
		return sb.toString();
	}

	/**
	 * An array of char containing the hex digits in ascending order.
	 */
	private static char[] hexDigits =
		new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f' };
	/**
	 * Convert a part of an array of bytes, into a string of space-separated hexadecimal numbers.<br>
	 * This method is proposed as a conveninence for debugging purposes.
	 * @param bytes the array that contains the sequence of byte values to convert.
	 * @param start the index to start at in the byte array.
	 * @param length the number of bytes to convert in the array.
	 * @return the cinverted bytes as a string of space-separated hexadecimal numbers.
	 */
	public static String dumpBytes(byte[] bytes, int start, int length)
	{
		StringBuilder sb = new StringBuilder();
		for (int i=start; i<start+length; i++)
		{
			if (i > start) sb.append(' ');
			sb.append(toHexString(bytes[i]));
		}
		return sb.toString();
	}
	
	/**
	 * Convert a byte value into a 2-digits hexadecimal value. The first digit is 0 if the value is less than 16.<br>
	 * If a value is negative, its 2-complement value is converted, otherwise the value itself is converted.
	 * @param b the byte value to convert.
	 * @return a string containing the 2-digit hexadecimal representation of the byte value.
	 */
	public static String toHexString(byte b)
	{
		int n = (b < 0) ? b + 256 : b;
		StringBuilder sb = new StringBuilder();
		sb.append(hexDigits[n / 16]);
		sb.append(hexDigits[n % 16]);
		return sb.toString();
	}
	
	/**
	 * Tranform a duration in milliseconds into a string with hours, minutes, seconds and milliseconds..
	 * @param elapsed the duration to transform, expressed in milliseconds.
	 * @return a string specifiying the duration in terms of hours, minutes, seconds and milliseconds.
	 */
	public static String toStringDuration(long elapsed)
	{
		StringBuilder sb = new StringBuilder();
		sb.append(elapsed / 3600000L).append(":");
		elapsed = elapsed % 3600000L;
		sb.append(elapsed / 60000L).append(":");
		elapsed = elapsed % 60000L;
		sb.append(elapsed / 1000L).append(".");
		sb.append(elapsed % 1000L);
		return sb.toString();
	}
}
