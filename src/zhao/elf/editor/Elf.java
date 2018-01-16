/*
 * [The "BSD licence"]
 * Copyright (c) 2014 Riddle Hsu
 * Modified CopyRight (c) 2017 ZhaoHai
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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.UnknownFormatConversionException;

import android.annotation.SuppressLint;
import zhao.elf.editor.IO.LEDataInputStream;
import zhao.elf.editor.IO.LEDataOutputStream;

public class Elf implements Closeable {
	// art/runtime/elf.h
	// Elf32 [Addr, Off, Word, Sword]=(int), [Half]=(short)
	// Elf64 [Addr, Off, Xword, Sxword]=(long), [Word]=(int), [Half]=(short)

	public static abstract class Ehdr {
		short e_type; // Type of file (see ET_* below)
		short e_machine; // Required architecture for this file (see EM_*)
		int e_version; // Must be equal to 1

		int e_flags; // Processor-specific flags
		short e_ehsize; // Size of ELF header, in bytes
		short e_phentsize; // Size of an entry in the program header table
		short e_phnum; // Number of entries in the program header table
		short e_shentsize; // Size of an entry in the section header table
		short e_shnum; // Number of entries in the section header table
		short e_shstrndx; // Sect hdr table index of sect name string table

		abstract long getProgramOffset();

		abstract long getSectionOffset();
	}

	static abstract class Elf_Phdr {
		int p_type; // Type of segment
		int p_offset; // File offset where segment is located, in bytes

		String flagsString() {
			return "(" + ((getFlags() & PF_R) != 0 ? "R" : "_") + ((getFlags() & PF_W) != 0 ? "W" : "_")
					+ ((getFlags() & PF_X) != 0 ? "X" : "_") + ")";
		}

		abstract long getFlags();

		String programType() {
			switch (p_type) {
			case PT_NULL:
				return "NULL";
			case PT_LOAD:
				return "Loadable Segment";
			case PT_DYNAMIC:
				return "Dynamic Segment";
			case PT_INTERP:
				return "Interpreter Path";
			case PT_NOTE:
				return "Note";
			case PT_SHLIB:
				return "PT_SHLIB";
			case PT_PHDR:
				return "Program Header";
			default:
				return "Unknown Section";
			}
		}
	}

	// Section header
	public static abstract class Elf_Shdr {
		int sh_name; // Section name (index into string table)
		int sh_type; // Section type (SHT_*)
		int sh_link; // Section type-specific header table index link
		int sh_info; // Section type-specific extra information
		int index;

		public abstract long getOffset();

		public abstract int getSize();
	}

	// --- Begin symbol table ---
	static abstract class Elf_Sym {
		int st_name; // Symbol name (index into string table)
		char st_info; // Symbol's type and binding attributes
		char st_other; // Must be zero; reserved
		short st_shndx; // Which section (header table index) it's defined in

		// These accessors and mutators correspond to the ELF32_ST_BIND,
		// ELF32_ST_TYPE, and ELF32_ST_INFO macros defined in the ELF
		// specification:
		char getBinding() {
			return (char) (st_info >> 4);
		}

		abstract long getSize();

		char getType() {
			return (char) (st_info & 0x0f);
		}

		void setBinding(char b) {
			setBindingAndType(b, getType());
		}

		void setBindingAndType(char b, char t) {
			st_info = (char) ((b << 4) + (t & 0x0f));
		}

		void setType(char t) {
			setBindingAndType(getBinding(), t);
		}
	}

	static class Elf32_Ehdr extends Ehdr {
		int e_entry; // Address to jump to in order to start program
		int e_phoff; // Program header table's file offset, in bytes
		int e_shoff; // Section header table's file offset, in bytes

		@Override
		long getProgramOffset() {
			return e_phoff;
		}

		@Override
		long getSectionOffset() {
			return e_shoff;
		}
	}

	// Program header for ELF32.
	static class Elf32_Phdr extends Elf_Phdr {
		int p_vaddr; // Virtual address of beginning of segment
		int p_paddr; // Physical address of beginning of segment (OS-specific)
		int p_filesz; // Num. of bytes in file image of segment (may be zero)
		int p_memsz; // Num. of bytes in mem image of segment (may be zero)
		int p_flags; // Segment flags
		int p_align; // Segment alignment constraint

		@Override
		public long getFlags() {
			return p_flags;
		}
	}

	static class Elf32_Shdr extends Elf_Shdr {
		int sh_flags; // Section flags (SHF_*)
		int sh_addr; // Address where section is to be loaded
		int sh_offset; // File offset of section data, in bytes
		int sh_size; // Size of section, in bytes
		int sh_addralign; // Section address alignment
		int sh_entsize; // Size of records contained within the section

		@Override
		public long getOffset() {
			return sh_offset;
		}

		@Override
		public int getSize() {
			return sh_size;
		}
	}

	// Symbol table entries for ELF32.
	static class Elf32_Sym extends Elf_Sym {
		int st_value; // Value or address associated with the symbol
		int st_size; // Size of the symbol

		@Override
		long getSize() {
			return st_size;
		}
	}

	static class Elf64_Ehdr extends Ehdr {
		long e_entry;
		long e_phoff;
		long e_shoff;

		@Override
		long getProgramOffset() {
			return e_phoff;
		}

		@Override
		long getSectionOffset() {
			return e_shoff;
		}
	}

	// Program header for ELF64.
	static class Elf64_Phdr extends Elf_Phdr {
		long p_vaddr; // Virtual address of beginning of segment
		long p_paddr; // Physical address of beginning of segment (OS-specific)
		long p_filesz; // Num. of bytes in file image of segment (may be zero)
		long p_memsz; // Num. of bytes in mem image of segment (may be zero)
		long p_flags; // Segment flags
		long p_align; // Segment alignment constraint

		@Override
		public long getFlags() {
			return p_flags;
		}
	}

	// Section header for ELF64 - same fields as ELF32, different types.
	static class Elf64_Shdr extends Elf_Shdr {
		long sh_flags;
		long sh_addr;
		long sh_offset;
		long sh_size;
		long sh_addralign;
		long sh_entsize;

		@Override
		public long getOffset() {
			return sh_offset;
		}

		@Override
		public int getSize() {
			return (int) sh_size;
		}
	}

	// Symbol table entries for ELF64.
	static class Elf64_Sym extends Elf_Sym {
		long st_value; // Value or address associated with the symbol
		long st_size; // Size of the symbol

		@Override
		long getSize() {
			return st_size;
		}
	};

	public static class ItemHelper {
		public String oldval;
		public String newVal;
		public byte[] data;
		public int sym_offset = -1; //符号索引

		public ItemHelper() {
		}

		public ItemHelper(String val) {
			this.oldval = val;
		}

		@Override
		public boolean equals(Object object) {
			return oldval.equals(((ItemHelper) object).oldval);
		}

		@Override
		public int hashCode() {
			return oldval.hashCode();
		}
	};

	final static char ElfMagic[] = { 0x7f, 'E', 'L', 'F', '\0' };
	final static int EI_CLASS = 4; // File class
	final static int EI_DATA = 5; // Data encoding
	final static int EI_NIDENT = 16;
	// --- Begin section header ---
	public static final String SHN_DYNSYM = ".dynsym";
	public static final String SHN_DYNSTR = ".dynstr";
	public static final String SHN_HASH = ".hash";

	public static final String SHN_RODATA = ".rodata";

	public static final String SHN_TEXT = ".text";
	public static final String SHN_DYNAMIC = ".dynamic";
	public static final String SHN_SHSTRTAB = ".shstrtab";
	// Special section indices.
	final static int SHN_UNDEF = 0; // Undefined, missing, irrelevant, or
	// meaningless
	// Section types
	final static int SHT_PROGBITS = 1; // Program-defined contents.
	final static int SHT_SYMTAB = 2; // Symbol table.
	final static int SHT_STRTAB = 3; // String table.

	final static int SHT_RELA = 4; // Relocation entries; explicit addends.

	final static int SHT_HASH = 5; // Symbol hash table.

	final static int SHT_DYNAMIC = 6; // Information for dynamic linking.;

	final static int SHT_DYNSYM = 11; // Symbol table.;

	// --- Begin program header ---
	// Segment types.
	final static int PT_NULL = 0; // Unused segment.

	final static int PT_LOAD = 1; // Loadable segment.;

	final static int PT_DYNAMIC = 2; // Dynamic linking information.
	final static int PT_INTERP = 3; // Interpreter pathname.
	final static int PT_NOTE = 4; // Auxiliary information.
	final static int PT_SHLIB = 5; // Reserved.
	final static int PT_PHDR = 6; // The program header table itself.
	final static int PT_TLS = 7; // The thread-local storage template.
	// Segment flag bits.
	final static int PF_X = 1; // Execute
	final static int PF_W = 2; // Write

	final static int PF_R = 4; // Read
	final static int PF_MASKOS = 0x0ff00000;// Bits for operating

	// system-specific semantics.
	final static int PF_MASKPROC = 0xf0000000; // Bits for processor-specific
	// semantics.

	/**
	 * 克隆ELF
	 * 
	 * @param bis
	 *            需要克隆的elf文件输入流
	 * @param os
	 *            文件输出路径
	 * @param packageName_O
	 *            原来的包名，如 zhao_apkcrack
	 * @param packageName_N
	 *            新包名，如 zhao_apkcracl，新包名长度必须和原来一样长
	 **/
	@SuppressWarnings("resource")
	public static boolean cloneElf(ByteArrayInputStream bis, OutputStream os, String packageName_O,
			String packageName_N) throws UnknownFormatConversionException, IOException {
		Elf elf = new Elf(bis);
		if (elf.error) {
			elf.close();
			return false;
		}
		// 整理dystyr
		for (ItemHelper item : elf.dy_items) {
			if (item.oldval.contains(packageName_O)) {
				String n = "Java_" + packageName_N;
				String s = "Java_" + packageName_O;
				item.newVal = n + item.oldval.substring(s.length());
			}
		}
		if (elf.ro_items != null) {
			packageName_O = packageName_O.replace("_", "/");
			packageName_N = packageName_N.replace("_", "/");
			// 整理rodata
			for (ItemHelper item : elf.ro_items) {
				if (item.oldval.contains(packageName_O)) {
					String start = item.oldval.substring(0, item.oldval.indexOf(packageName_O));
					item.newVal = start + packageName_N
							+ item.oldval.substring(packageName_N.length() + start.length());
				}
			}
		}
		elf.writeELF(os);
		return !elf.error;
	}

	public static boolean isElf(File f) {
		long n = 0;
		try {
			RandomAccessFile raf = new RandomAccessFile(f, "r");
			n = raf.readInt();
			raf.close();
		} catch (IOException ex) {
			System.out.println(ex);
		}
		return n == 0x7F454C46;
	}

	public static byte[] readFile(File file) throws FileNotFoundException, IOException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		InputStream is = new FileInputStream(file);
		byte buffer[] = new byte[2048];
		int count;
		while ((count = is.read(buffer)) != -1) {
			bos.write(buffer, 0, count);
		}
		is.close();
		return bos.toByteArray();
	}

	public List<ItemHelper> dy_items, ro_items;

	final byte[] e_ident = new byte[EI_NIDENT]; // ELF Identification bytes
	private LEDataInputStream mReader;

	private final Ehdr mHeader;

	private final Elf_Shdr[] mSectionHeaders;

	private byte[] mStringTable;

	private byte mRoDataStringTable[];
	// private List<String> mRoDataStrings;
	Elf_Phdr[] mProgHeaders;
	Elf_Sym[] mDynamicSymbols;
	Elf_Sym[] mHashSymbols;

	byte[] mDynStringTable;

	byte[] mDynHashTable;

	private int num_buckets;

	private int num_chains;

	// These could probably be memoized.
	private int buckets[];

	private int chains[];

	private boolean error; // 解析时是否有错误

	public Elf(ByteArrayInputStream bis) throws IOException, UnknownFormatConversionException {
		dy_items = new ArrayList<ItemHelper>();
		final LEDataInputStream r = mReader = new LEDataInputStream(bis);
		r.readFully(e_ident);
		if (!checkMagic()) {
			throw new UnknownFormatConversionException("Invalid elf magic");
		}
		r.setIsLittleEndian(isLittleEndian());

		final boolean is64bit = is64bit();
		if (is64bit) {
			Elf64_Ehdr header = new Elf64_Ehdr();
			header.e_type = r.readShort();
			header.e_machine = r.readShort();
			header.e_version = r.readInt();
			header.e_entry = r.readLong();
			header.e_phoff = r.readLong();
			header.e_shoff = r.readLong();
			mHeader = header;
		} else {
			Elf32_Ehdr header = new Elf32_Ehdr();
			header.e_type = r.readShort();
			header.e_machine = r.readShort();
			header.e_version = r.readInt();
			header.e_entry = r.readInt();
			header.e_phoff = r.readInt();
			header.e_shoff = r.readInt();
			mHeader = header;
		}
		final Ehdr h = mHeader;
		h.e_flags = r.readInt();
		h.e_ehsize = r.readShort();
		h.e_phentsize = r.readShort();
		h.e_phnum = r.readShort();
		h.e_shentsize = r.readShort();
		h.e_shnum = r.readShort();
		h.e_shstrndx = r.readShort();

		mSectionHeaders = new Elf_Shdr[h.e_shnum];
		for (int i = 0; i < h.e_shnum; i++) {
			final long offset = h.getSectionOffset() + (i * h.e_shentsize);
			// Logger.write(" h.getSectionOffset()=" + h.getSectionOffset() +
			// "\n");
			// Logger.write("h.e_shentsize=" + h.e_shentsize + "\n");
			r.seek(offset);
			if (is64bit) {
				Elf64_Shdr secHeader = new Elf64_Shdr();
				secHeader.sh_name = r.readInt();
				secHeader.sh_type = r.readInt();
				secHeader.sh_flags = r.readLong();
				secHeader.sh_addr = r.readLong();
				secHeader.sh_offset = r.readLong();
				secHeader.sh_size = r.readLong();
				secHeader.sh_link = r.readInt();
				secHeader.sh_info = r.readInt();
				secHeader.sh_addralign = r.readLong();
				secHeader.sh_entsize = r.readLong();
				secHeader.index = i;
				mSectionHeaders[i] = secHeader;
			} else {
				Elf32_Shdr secHeader = new Elf32_Shdr();
				secHeader.sh_name = r.readInt();
				secHeader.sh_type = r.readInt();
				secHeader.sh_flags = r.readInt();
				secHeader.sh_addr = r.readInt();
				secHeader.sh_offset = r.readInt();
				secHeader.sh_size = r.readInt();
				secHeader.sh_link = r.readInt();
				secHeader.sh_info = r.readInt();
				secHeader.sh_addralign = r.readInt();
				secHeader.sh_entsize = r.readInt();
				secHeader.index = i;
				mSectionHeaders[i] = secHeader;
			}
		}
		if (h.e_shstrndx > -1 && h.e_shstrndx < mSectionHeaders.length) {
			Elf_Shdr strSec = mSectionHeaders[h.e_shstrndx];
			// Logger.write("lastoff=" +strSec.getOffset() + "\n" );
			if (strSec.sh_type == SHT_STRTAB) {
				int strSecSize = strSec.getSize();
				mStringTable = new byte[strSecSize];
				r.seek(strSec.getOffset());
				r.readFully(mStringTable);
				for (Elf_Shdr sec : mSectionHeaders) {
					// Logger.write(getString(sec.sh_name));
					System.out.println(getString(sec.sh_name));
				}
			} else {
				throw new UnknownFormatConversionException("Wrong string section e_shstrndx=" + h.e_shstrndx);
			}
		} else {
			throw new UnknownFormatConversionException("Invalid e_shstrndx=" + h.e_shstrndx);
		}
		try {
			if (!readMore(dy_items)) {
				error = true;
			}
		} catch (OutOfMemoryError e) {
			error = true;
		}
	}

	public Elf(ByteArrayInputStream bis, ResourceCallBack callBack) throws IOException, UnknownFormatConversionException {
		this(bis);
		for (ItemHelper item : this.dy_items) {
			ResourceHelper helper = new ResourceHelper();
			helper.VALUE = item.oldval;
			helper.TYPE = "dynstr";
			callBack.back(helper);
		}
		if (this.mRoDataStringTable != null) {
			for (ItemHelper item : this.ro_items) {
				ResourceHelper helper = new ResourceHelper();
				helper.VALUE = item.oldval;
				helper.TYPE = "rodata";
				callBack.back(helper);
			}
		}

	}

	public Elf(File file) throws IOException, UnknownFormatConversionException {
		this(new ByteArrayInputStream(readFile(file)));
	}

	public Elf(String file) throws IOException, UnknownFormatConversionException {
		this(new File(file));
	}

	public Elf(String file, boolean closeNow) throws IOException, UnknownFormatConversionException {
		this(file);
		if (closeNow) {
			mReader.close();
		}
	}

	final boolean checkMagic() {
		return e_ident[0] == ElfMagic[0];
	}

	@Override
	public void close() throws IOException {
		mReader.close();
	}

	private String fillString(String string, int length) {
		StringBuilder sb = new StringBuilder();
		sb.append(string);
		if (string.length() < length) {
			int remaining = length - string.length();
			while (remaining-- > 0) {
				sb.append(" ");
			}
		}
		return sb.toString();
	}

	/**
	 * 查找是否有这个符号,同时返回索引
	 * */
	public int find(String str) {
		long hash = ELFHash(str);
		for (int i = buckets[(int) (hash % num_buckets)]; i != 0; i = chains[i]) {
			Elf_Sym ds = mDynamicSymbols[i];
			String string = getDynString(ds.st_name);
			System.out.println(string);
			if (string.equals(str)) {
				//Logger.write("str=" + str + " " + "pos=" + i + "\n");
				return i;
			}
		}
		return -1;
	}
	
	final byte getDataEncoding() {
		return e_ident[EI_DATA];
	}

	public final String getDynString(int index) {
		if (index == SHN_UNDEF) {
			return "SHN_UNDEF";
		}
		int start = index;
		int end = index;
		while (mDynStringTable[end] != '\0') {
			end++;
		}
		return new String(mDynStringTable, start, end - start);
	}

	final byte getFileClass() {
		return e_ident[EI_CLASS];
	}

	public Ehdr getHeader() {
		return mHeader;
	}

	public LEDataInputStream getReader() {
		return mReader;
	}

	public final Elf_Shdr getSectionByName(String name) {
		for (Elf_Shdr sec : mSectionHeaders) {
			if (name.equals(getString(sec.sh_name))) {
				return sec;
			}
		}
		return null;
	}

	public Elf_Shdr[] getSectionHeaders() {
		return mSectionHeaders;
	}

	public final String getString(int index) {
		if (index == SHN_UNDEF) {
			return "SHN_UNDEF";
		}
		int start = index;
		int end = index;
		while (mStringTable[end] != '\0') {
			end++;
		}
		return new String(mStringTable, start, end - start);
	}

	public final boolean is64bit() {
		return getFileClass() == 2;
	}

	public final boolean isLittleEndian() {
		return getDataEncoding() == 1;
	}

	private boolean readMore(List<ItemHelper> items) throws IOException {
		final Ehdr h = mHeader;
		final LEDataInputStream r = mReader;
		final boolean is64bit = is64bit();
		
		//读取Hash表
		Elf_Shdr dyhash = getSectionByName(SHN_HASH);
		if (dyhash != null) {
			r.seek(dyhash.getOffset());
			num_buckets = r.readInt();
			num_chains = r.readInt();
			buckets = r.readIntArray(num_buckets);
			chains = r.readIntArray(num_chains);
			int actual = num_buckets * 4 + num_chains * 4 + 8;
			if (dyhash.getSize() != actual) {
				throw new IOException("Error reading string table (read " + actual + "bytes, expected to " + "read "
						+ dyhash.getSize() + "bytes).");
			}
		}
		
		Elf_Shdr dynsym = getSectionByName(SHN_DYNSYM);
		if (dynsym != null) {
			r.seek(dynsym.getOffset());
			int len = dynsym.getSize() / (is64bit ? 24 : 16); // sizeof
																// Elf_Sym
			mDynamicSymbols = new Elf_Sym[len];

			for (int i = 0; i < len; i++) {
				if (is64bit) {
					Elf64_Sym dsym = new Elf64_Sym();
					dsym.st_name = r.readInt();
					dsym.st_info = (char) r.readByte();
					dsym.st_other = (char) r.readByte();
					dsym.st_value = r.readLong();
					dsym.st_size = r.readLong();
					dsym.st_shndx = r.readShort();
					mDynamicSymbols[i] = dsym;
				} else {
					Elf32_Sym dsym = new Elf32_Sym();
					dsym.st_name = r.readInt();
					dsym.st_value = r.readInt();
					dsym.st_size = r.readInt();
					dsym.st_info = (char) r.readByte();
					dsym.st_other = (char) r.readByte();
					dsym.st_shndx = r.readShort();
					mDynamicSymbols[i] = dsym;

					/*
					 * if (dsym.st_size > 0) { Elf_Shdr sec =
					 * mSectionHeaders[dsym.st_shndx]; }
					 */
				}
			}

			Elf_Shdr dynLinkSec = mSectionHeaders[dynsym.sh_link];
			r.seek(dynLinkSec.getOffset());
			mDynStringTable = new byte[dynLinkSec.getSize()];
			r.readFully(mDynStringTable);

			String string = new String(mDynStringTable);
			String[] mDyStrs = string.split("\0");
			for (String mDyStr : mDyStrs) {
				if (mDyStr.length() == 0) {
					continue;
				}
				ItemHelper item = new ItemHelper();
				item.oldval = mDyStr;
				items.add(item);
				item.sym_offset = find(mDyStr);
			}
		}


		mProgHeaders = new Elf_Phdr[h.e_phnum];
		for (int i = 0; i < h.e_phnum; i++) {
			final long offset = h.getProgramOffset() + (i * h.e_phentsize);
			r.seek(offset);
			if (is64bit) {
				Elf64_Phdr progHeader = new Elf64_Phdr();
				progHeader.p_type = r.readInt();
				progHeader.p_offset = r.readInt();
				progHeader.p_vaddr = r.readLong();
				progHeader.p_paddr = r.readLong();
				progHeader.p_filesz = r.readLong();
				progHeader.p_memsz = r.readLong();
				progHeader.p_flags = r.readLong();
				progHeader.p_align = r.readLong();
				mProgHeaders[i] = progHeader;
			} else {
				Elf32_Phdr progHeader = new Elf32_Phdr();
				progHeader.p_type = r.readInt();
				progHeader.p_offset = r.readInt();
				progHeader.p_vaddr = r.readInt();
				progHeader.p_paddr = r.readInt();
				progHeader.p_filesz = r.readInt();
				progHeader.p_memsz = r.readInt();
				progHeader.p_flags = r.readInt();
				progHeader.p_align = r.readInt();
				mProgHeaders[i] = progHeader;
			}
		}

		Elf_Shdr roData = getSectionByName(SHN_RODATA);
		if (roData != null) {
			r.seek(roData.getOffset());
			mRoDataStringTable = new byte[roData.getSize()];
			r.readFully(mRoDataStringTable);
			ro_items = new ArrayList<ItemHelper>();

			int end = 0;
			while (end != mRoDataStringTable.length) {
				while (end != mRoDataStringTable.length && mRoDataStringTable[end++] == 0)
					; // 去除开头可能的结尾
				int start = end;
				//对于字符串末尾有空格的情况，也要一同取出，空格ascii是20,而0是结尾
				while (end != mRoDataStringTable.length && mRoDataStringTable[end++] != 0)
					;
				ItemHelper item = new ItemHelper();
				item.oldval = new String(mRoDataStringTable, start - 1, end - start);
				item.data = new byte[end - start];
				System.arraycopy(mRoDataStringTable, start - 1, item.data, 0, item.data.length);
				ro_items.add(item);
			}
		}
		return true;
	}

	public long ELFHash(String strUri) {
		long hash = 0;
		long x = 0;
		for (int i = 0; i < strUri.length(); i++) {
			hash = (hash << 4) + strUri.charAt(i);
			if ((x = hash & 0xF0000000L) != 0) {
				hash ^= (x >> 24);
				hash &= ~x;
			}
		}
		return (hash & 0x7FFFFFFF);
	}
	
	/**
	 * 写入符号表hash
	 */
	private final void writeDynHash(List<ItemHelper> items, LEDataOutputStream lmOut) throws IOException {
		lmOut.writeInt(num_buckets);
		lmOut.writeInt(num_chains);
		int buckets_t[] = new int[num_buckets];
		int chains_t[] = new int[num_chains];
		
		for (ItemHelper item : items) {
			//没有符号索引，说明该字符串不是符号表中的
			if (item.sym_offset == -1) {
				continue;
			}
			int offset = (int) (ELFHash(item.newVal == null ? item.oldval : item.newVal) % num_buckets);
			
			if (buckets_t[offset] == 0) {
				buckets_t[offset] = item.sym_offset;
			} else {
				for (offset = buckets_t[offset];offset != 0; offset = chains_t[offset]) {
					if (chains_t[offset] == 0) {
						chains_t[offset] = item.sym_offset;
						break;
					}

				}
			}
		}
		lmOut.writeIntArray(buckets_t);
		lmOut.writeIntArray(chains_t);
	}

	/**
	 * 写符号名
	 * 
	 * @return 写入的实际大小
	 ***/
	private final long writeDynString(List<ItemHelper> items, LEDataOutputStream lmOut) throws IOException {

		long offset = 0;
		long len = 0;
		for (ItemHelper item : items) {
			String old_string = item.oldval;
			if (old_string.equals("SHN_UNDEF"))
				continue;
			// String new_string = item.newVal;
			lmOut.writeByte((byte) '\0');
			byte data[] = old_string.getBytes();
			offset += data.length; // 切勿调换顺序

			if (item.newVal != null) {
				item.newVal = fillString(item.newVal, item.oldval.length());
				data = item.newVal.getBytes();
			}
			len += data.length;
			lmOut.writeFully(data);
			offset += 1;
			len += 1;
		}
		// 写入余下部分
		if (mDynStringTable.length - (int) offset <= 0)
			return len;
		lmOut.writeFully(mDynStringTable, (int) offset, mDynStringTable.length - (int) offset);
		len += mDynStringTable.length - (int) offset;
		return len;
	}

	/**
	 * 写ELF 
	 * @param os 文件输出流
	 ****/
	public final void writeELF(OutputStream os) throws IOException {
		final LEDataOutputStream lmOut = new LEDataOutputStream(os);
		Elf_Shdr dynsym = getSectionByName(SHN_DYNSYM);
		Elf_Shdr dynLinkSec = mSectionHeaders[dynsym.sh_link];
		long offset = dynLinkSec.getOffset();
		Elf_Shdr dyhash = getSectionByName(SHN_HASH);
		long offset2 = dyhash.getOffset();
		// 需要注意的是哪个段在前，不是所有的elf文件段都是一样顺序的
		if (offset > offset2) {
			writeExtra(0, offset2, lmOut); // 写入前面部分
			writeDynHash(dy_items, lmOut); // 写入符号名hash

			offset2 += num_buckets * 4 + num_chains * 4 + 8;
			writeExtra(offset2, offset, lmOut); // 写入符号名和符号名hash之间的数据
			offset2 = offset;
			writeDynString(dy_items, lmOut); // 写入符号名
			offset2 += mDynStringTable.length;
			offset = offset2;
		} else {
			writeExtra(0, offset, lmOut); // 写入前面部分
			writeDynString(dy_items, lmOut); // 写入符号名
			offset += mDynStringTable.length;
			writeExtra(offset, offset2, lmOut); // 写入符号名和符号名hash之间的数据
			offset = offset2;
			writeDynHash(dy_items, lmOut); // 写入符号名hash
			offset += num_buckets * 4 + num_chains * 4 + 8;
		}
		// 写RoData
		Elf_Shdr roData = getSectionByName(SHN_RODATA);
		if (roData != null) {
			writeExtra(offset, roData.getOffset(), lmOut);
			offset = roData.getOffset();
			writeRodataBytes();
			lmOut.writeFully(mRoDataStringTable);
			offset += mRoDataStringTable.length;
		}

		writeExtra(offset, mReader.size, lmOut); // 写入余下部分
		lmOut.close();
		close();
	}

	private void writeExtra(long offset1, long offset2, LEDataOutputStream lmOut) throws IOException {
		long len = offset2 - offset1;
		if (len <= 0)
			return;
		int buf_len = 2048;
		long remaining = len;
		mReader.seek(offset1);
		byte buffer[] = new byte[buf_len];
		while (remaining != 0) {
			if (buf_len <= remaining) {
				mReader.readFully(buffer);
				lmOut.writeFully(buffer);
				remaining -= buf_len;
			} else {
				mReader.readFully(buffer, 0, (int) remaining);
				lmOut.writeFully(buffer, 0, (int) remaining);
				remaining = 0;
			}
		}
	}

	// 排序字符串，由于字符串在arsc中是一一对应的，所以不能改变原来的一一对应，需要将列表进行排序
	/*
	 * @SuppressLint("DefaultLocale") public void sortStringBlock(String src,
	 * String tar/* , SQLiteDatabase... db
	 *//*
		 * ) { /* if (!isSorted) { isSorted = true; }
		 */
	// 查找原字符串在strings这个有序的数组中的位置，以此确定修改后的字符串对应的位置
	/*
	 * int position = mRoDataStrings.indexOf(src); //
	 * 如果在strings中查找到这个字符串，并且修改后的字符串不为空 if (position >= 0 && !tar.equals("")) {
	 * // 从strings数组中移除原来的内容 // strings.remove(position); // 将新字符串添加到相应的位置
	 * mRoDataStrings.set(position, tar); /* if (db.length == 1 && db[0] !=
	 * null) { // 添加到词典 ContentValues values = new ContentValues();
	 * values.put("word", src.toLowerCase()); values.put("explain", tar);
	 * db[0].insert("Words", null, values); }
	 */
	/*
	 * } }
	 * 
	 * public void sortRoString(List<String> roStr_s, List<String> roTar_s) {
	 * int index = 0; for (String string : roStr_s) { // 排序字符串
	 * sortStringBlock(string, roTar_s.get(index)); index++; } }
	 * 
	 * public ByteArrayOutputStream writeRoString() throws
	 * UnsupportedEncodingException, IOException { ByteArrayOutputStream bos =
	 * new ByteArrayOutputStream(); int len = 0; int zc = 0; for (String string
	 * : mRoDataStrings) { /* Logger.write("string=" + string); if
	 * (string.length() == 0) { bos.write(0); len++; zc++; } else { if (zc != 0
	 * && zc % 4 != 0) { zc = 0; while (len % 4 != 0) { bos.write(0); len++; } }
	 * byte[] data = string.getBytes(); bos.write(data); bos.write(0); len +=
	 * data.length; } } if (len % 4 == 0) { bos.write(0); len++; }
	 */
	/*
	 * if (string.length() == 0) { bos.write(0); len++; } else { byte[] data =
	 * string.getBytes(); bos.write(data); bos.write(0); len += data.length + 1;
	 * } } while (len % 4 != 0) { bos.write(0); len++; } bos.close(); return
	 * bos; }
	 * 
	 * class O { long offset; long size = -1; }
	 * 
	 * Map<Long, O> n_offsets = new TreeMap<Long, O>();
	 * 
	 * private void recordOffset(int padding) { Elf_Shdr rodata =
	 * getSectionByName(SHN_RODATA); for (Elf_Shdr sec : mSectionHeaders) { if
	 * (sec.getOffset() < rodata.getOffset()) { continue; } else { final long
	 * offset = mHeader.getSectionOffset() + padding + (sec.index *
	 * mHeader.e_shentsize); O o = new O(); o.offset = sec.getOffset() +
	 * padding; if (sec.getOffset() == rodata.getOffset()) { o.size =
	 * mRoDataStringTable.length + padding; } n_offsets.put(offset, o); } } }
	 * 
	 * public void fixOffset(OutputStream os, int padding) throws
	 * FileNotFoundException, IOException { final LEDataOutputStream lmOut = new
	 * LEDataOutputStream(os); mReader = new LEDataInputStream(
	 * zhao.apkmodifier.Utils.FileUtils.InputStream2ByteArray(new
	 * FileInputStream("/sdcard/3.so"))); long s_off = 0; if (is64bit()) {
	 * writeExtra(0, 40, lmOut); lmOut.writeLong(mHeader.getSectionOffset() +
	 * padding); s_off += 48; } else { writeExtra(0, 32, lmOut);
	 * lmOut.writeInt((int) (mHeader.getSectionOffset() + padding)); s_off +=
	 * 36; }
	 * 
	 * for (long offset : n_offsets.keySet()) { // Logger.write("offset" +
	 * offset + "\n"); O o = n_offsets.get(offset); writeExtra(s_off, offset,
	 * lmOut); // 写入余下部分 s_off = offset; if (is64bit()) { if (o.size != -1) { //
	 * 写rodata段大小 writeExtra(s_off, s_off + 32, lmOut); lmOut.writeLong(o.size);
	 * s_off += 40; } else { writeExtra(s_off, s_off + 24, lmOut);
	 * lmOut.writeLong(o.offset); s_off += 32; } } else {
	 * 
	 * if (o.size != -1) { // 写rodata段大小 writeExtra(s_off, s_off + 20, lmOut);
	 * lmOut.writeInt((int) o.size); s_off += 24; } else { writeExtra(s_off,
	 * s_off + 16, lmOut); lmOut.writeInt((int) o.offset); s_off += 20; }
	 * 
	 * } }
	 * 
	 * writeExtra(s_off, mReader.size, lmOut); // 写入余下部分 lmOut.close(); }
	 * 
	 * public final void writeELF2(OutputStream os) throws IOException { final
	 * LEDataOutputStream lmOut = new LEDataOutputStream(new
	 * FileOutputStream("/sdcard/3.so")); ByteArrayOutputStream bos =
	 * writeRoString(); // 偏移 int padding = bos.size() -
	 * mRoDataStringTable.length; // 记录偏移 recordOffset(padding); Elf_Shdr roData
	 * = getSectionByName(SHN_RODATA); int offset = 0; if (roData != null) {
	 * writeExtra(0, roData.getOffset(), lmOut); offset = (int)
	 * roData.getOffset(); lmOut.writeFully(bos.toByteArray()); offset +=
	 * mRoDataStringTable.length; } writeExtra(offset, mReader.size, lmOut); //
	 * 写入余下部分 lmOut.close(); close(); fixOffset(os, padding); }
	 */

	/**
	 * 整理数据(字符串)
	 **/
	@SuppressLint("DefaultLocale")
	public void sortStrData(List<String> source, List<String> target, List<ItemHelper> items) {
		int index = 0;
		for (String string : target) {
			if (!string.equals("")) {
				int i = items.indexOf(new ItemHelper(source.get(index)));
				if (i == -1) { // 乱码
					continue;
				}

				ItemHelper item = items.get(i);
				item.newVal = string;
			}
			index++;
		}
	}

	public void writeRodataBytes() throws UnsupportedEncodingException {
		for (ItemHelper item : ro_items) {
			if (item.newVal != null && !item.newVal.equals("")) {
				byte[] s_data = item.data;
				// 找到偏移位置
				int pos = findBytesPos(mRoDataStringTable, s_data);
				if (pos == -1) {
					continue;
				}
				byte[] data = item.newVal.getBytes();
				for (byte b : data) { // 替换
					mRoDataStringTable[pos++] = b;
				}
				int len_s = s_data.length - data.length;
				while (len_s-- > 0) {
					mRoDataStringTable[pos++] = 00; //替换成空格，以保证文件大小不变
				}
			}
		}
	}
	
	/** 在内存中搜索数据 **/
	public static int findBytesPos(byte[] data, byte[] found) {
		for (int i = 0; i < data.length; i++) {
			boolean bFound = true;
			for (int j = 0; j < found.length; j++)
				bFound = bFound && data[i + j] == found[j];
			if (bFound) {
				return i;
			}
		}
		return -1;
	}	
}
