/*
 * [The "BSD licence"]
 * Copyright (c) 2017 ZhaoHai
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
 * THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
/*******************************************************************************
 * @author zhaohai
 * 二进制文件输出处理工具类
 * 2015.10
 */

package zhao.elf.editor.IO;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class LEDataOutputStream {

	/** 二进制文件输出流 */
	private DataOutputStream dos;

	/** 构造函数 */
	public LEDataOutputStream(OutputStream out) {
		dos = new DataOutputStream(out);
	}

	/**
	 * 关闭流
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		dos.flush();
		dos.close();
	}

	/**
	 * 获取流的大小
	 * 
	 * @return
	 */
	public int size() {
		return dos.size();
	}

	/**
	 * 写入一个字节
	 * 
	 * @param b
	 * @throws IOException
	 */
	public void writeByte(byte b) throws IOException {
		dos.writeByte(b);
	}

	/**
	 * 写入length个空字节
	 * 
	 * @param length
	 * @throws IOException
	 */
	public void writeBytes(int length) throws IOException {
		for (int i = 0; i < length; i++)
			dos.writeByte(0);
	}

	/**
	 * 写入字符数组
	 * 
	 * @param charbuf
	 * @throws IOException
	 */
	public void writeCharArray(char[] charbuf) throws IOException {
		int length = charbuf.length;
		for (int i = 0; i < length; i++)
			writeShort((short) charbuf[i]);
	}

	/**
	 * 写入字节数组
	 * 
	 * @param b
	 * @throws IOException
	 */
	public void writeFully(byte[] b) throws IOException {
		dos.write(b, 0, b.length);
	}

	/**
	 * 写入字节数组
	 * 
	 * @param buffer
	 * @param offset
	 * @param count
	 * @throws IOException
	 */
	public void writeFully(byte[] buffer, int offset, int count) throws IOException {
		dos.write(buffer, offset, count);
	}

	/**
	 * 写入一个32位的int型数据
	 * 
	 * @param i
	 * @throws IOException
	 */
	public void writeInt(int i) throws IOException {
		dos.writeByte(i & 0xff);
		dos.writeByte((i >> 8) & 0xff);
		dos.writeByte((i >> 16) & 0xff);
		dos.writeByte((i >> 24) & 0xff);
	}

	/**
	 * 写入32位的int型数组
	 * 
	 * @param buf
	 * @throws IOException
	 */
	public void writeIntArray(int[] buf) throws IOException {
		writeIntArray(buf, 0, buf.length);
	}

	/**
	 * 写入32位的int型数组
	 * 
	 * @param buf
	 * @param s
	 * @param end
	 * @throws IOException
	 */
	private void writeIntArray(int[] buf, int s, int end) throws IOException {
		for (; s < end; s++)
			writeInt(buf[s]);
	}

	/**
	 * 写入一个64位的long型数据
	 * 
	 * @param i
	 * @throws IOException
	 */
	public void writeLong(long l) throws IOException {
		dos.writeByte((int) (l & 0xff));
		dos.writeByte((int) ((l >> 8) & 0xff));
		dos.writeByte((int) ((l >> 16) & 0xff));
		dos.writeByte((int) ((l >> 24) & 0xff));
		dos.writeByte((int) ((l >> 32) & 0xff));
		dos.writeByte((int) ((l >> 40) & 0xff));
		dos.writeByte((int) ((l >> 48) & 0xff));
		dos.writeByte((int) ((l >> 56) & 0xff));
	}

	/**
	 * 写入包名
	 * 
	 * @param name
	 * @throws IOException
	 */
	public void writeNulEndedString(String name) throws IOException {
		char[] ch = name.toCharArray();
		int length = ch.length;

		for (int i = 0; i < length; i++)
			writeShort((short) ch[i]);

		writeBytes(128 * 2 - length * 2);
	}

	/** 写入一个16位的short型数据 */
	public void writeShort(short s) throws IOException {
		dos.writeByte(s & 0xff);
		dos.writeByte((s >>> 8) & 0xff);
	}
}
