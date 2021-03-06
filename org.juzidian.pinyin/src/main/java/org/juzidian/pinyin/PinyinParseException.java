/*
 * Copyright Nathan Jones 2012
 * 
 * This file is part of Juzidian.
 *
 * Juzidian is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Juzidian is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Juzidian.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.juzidian.pinyin;

/**
 * Indicates parsing of an invalid Pinyin was attempted.
 */
public class PinyinParseException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	public PinyinParseException(final String message) {
		super(message);
	}

	public PinyinParseException(final String message, final Throwable cause) {
		super(message, cause);
	}

}
