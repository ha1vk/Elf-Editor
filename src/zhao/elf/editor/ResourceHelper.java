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
package zhao.elf.editor;

// 一个储存键的类
public class ResourceHelper implements Comparable<ResourceHelper> {

	public String CONFIG;
	public String NAME;
	public String TYPE;
	public String VALUE;
	public String NEW_VALUE;
	public boolean editableString;
	public int offset;
	public int data;

	public ResourceHelper() {

	}

	public ResourceHelper(String NAME, String VALUE) {
		this.NAME = NAME;
		this.VALUE = VALUE;
	}

	@Override
	public int compareTo(ResourceHelper arg0) {
		return offset - arg0.offset;
	}

	@Override
	public boolean equals(Object obj) {
		return NAME.equals(((ResourceHelper) obj).NAME) && VALUE.equals(((ResourceHelper) obj).VALUE);
	}
}
