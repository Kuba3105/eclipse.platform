/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.console;

import org.eclipse.swt.graphics.Color;

/**
 * Used to write messages to a message console. A message console may have more
 * than one stream connected to it. Each stream may be displayed in a different
 * color.
 * 
 * @since 3.0
 */
public class MessageConsoleStream {
	
	private MessageConsole fConsole = null;
	
	private Color fColor = null;
	
	/**
	 * Constructs a new stream connected to the given console.
	 * 
	 * @param partitioner the partitioner to write messages to
	 */
	public MessageConsoleStream(MessageConsole console) {
		fConsole = console;
	}
	
	/**
	 * Appends the specified message to this stream.
	 * 
	 * @param message message to append
	 */
	public void print(String message) {
		fConsole.appendToDocument(message, this);
	}
	
	
	/**
	 * Appends a line separator string to this stream.
	 */
	public void println() {
		print("\n"); //$NON-NLS-1$
	}	
	
	/**
	 * Appends the specified message to this stream, followed by a line
	 * separator string.
	 * 
	 * @param message message to print
	 */
	public void println(String message) {
		print(message);
		println();
	}	
	
	/**
	 * Sets the color of this message stream
	 * 
	 * @param color color of this message stream, possibly <code>null</code>
	 */
	public void setColor(Color color) {
		Color old = fColor;
		fColor = color;
		fConsole.firePropertyChange(this, MessageConsole.P_STREAM_COLOR, old, color);
	}
	
	/**
	 * Returns the color of this message stream, or <code>null</code>
	 * if default.
	 * 
	 * @return the color of this message stream, or <code>null</code>
	 */
	public Color getColor() {
		return fColor;
	}
}