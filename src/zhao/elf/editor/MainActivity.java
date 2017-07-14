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

/**
 * @author zhaohai
 * @time 2015.10
 * ARSC编辑器主界面
 * */
package zhao.elf.editor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UnknownFormatConversionException;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.InputFilter;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	/**
	 * 一个用来获取解析后的资源的线程
	 * 
	 * @author zhaohai
	 */
	class GetTask extends AsyncTask<String, Void, Void> {
		// 进度条
		private ProgressDialog dlg;

		// 执行耗时任务
		@Override
		protected Void doInBackground(String... params) {
			if (RESOURCES != null) {
				////////////////////////////////////////////////////////////////
				if (checkChanged()) {
					// 整理RoData
					if (textCategory.getText().toString().equals("dynstr")) {
						elfParser.sortStrData(txtOriginal, txtTranslated, elfParser.ro_items);
					} else { // 整理Dynstr
						elfParser.sortStrData(txtOriginal, txtTranslated, elfParser.dy_items);
					}
					isChanged = true;
				}
				////////////////////////////////////////////////////////////

				txtOriginal.clear();
				txtTranslated.clear();

				for (ResourceHelper resource : RESOURCES.values()) {
					// 获取资源的值
					String VALUE = resource.VALUE;
					// 获取资源类型
					String TYPE = resource.TYPE;

					if (TYPE.equals(params[0])) {
						// 向储存字符串的列表中添加字符串成员
						txtOriginal.add(VALUE);
					}
				}
				initList();
			}
			return null;
		}

		// 耗时任务执行完毕后的事件处理
		@Override
		protected void onPostExecute(Void result) {
			// 隐藏进度条
			dlg.dismiss();
			// 通知数据适配器更新数据
			mAdapter.notifyDataSetInvalidated();
		}

		// 耗时任务开始前执行的任务
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			dlg = new ProgressDialog(MainActivity.this);
			dlg.setCancelable(false);
			dlg.setTitle(R.string.parsing);
			dlg.show();
		}

	}

	/**
	 * @author zhaohai 一个用来解析ARSC的线程
	 */
	class ParseTask extends AsyncTask<InputStream, Integer, String> {
		// 进度条
		private ProgressDialog dlg;
		// 资源回调接口
		private ResourceCallBack callback;

		// 执行耗时任务
		@Override
		protected String doInBackground(InputStream... params) {

			try {
				parseELF(callback, params[0]);
			} catch (UnknownFormatConversionException | IOException e) {
				e.printStackTrace();
				return "failed";
			}
			return getString(R.string.success);
		}

		// 耗时任务执行完毕后的事件处理
		@Override
		protected void onPostExecute(String result) {
			// 隐藏进度条
			dlg.dismiss();
			// 如果返回的结果不是成功
			if (!result.equals(getString(R.string.success))) {
				// 显示错误信息
				showMessage(MainActivity.this, result).show();
				return;
			}
			// 对资源种类列表排序
			Collections.sort(Types);
			// 开启新线程
			AsyncTask<String, Void, Void> getTask = new GetTask();
			getTask.execute(textCategory.getText().toString());
		}

		// 耗时任务开始前执行的任务
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			dlg = new ProgressDialog(MainActivity.this);
			dlg.setCancelable(false);
			dlg.setTitle(R.string.parsing);
			dlg.show();
			textCategory.setText("dynstr");
			// 如果储存资源类型的列表未初始化
			if (Types == null) {
				// 初始化储存资源类型的列表
				Types = new ArrayList<String>();
			}
			// 实现资源回调接口
			callback = new ResourceCallBack() {
				@Override
				public void back(ResourceHelper helper) {
					if (RESOURCES == null) {
						RESOURCES = new LinkedHashMap<String, ResourceHelper>();
					}
					RESOURCES.put(helper.VALUE, helper);
					// 如果资源种类集合中不存在该种类
					if (!Types.contains(helper.TYPE)) {
						// 向其中添加该种类
						Types.add(helper.TYPE);
					}
				}
			};
		}

		// 更新ui界面
		@Override
		protected void onProgressUpdate(Integer... values) {
			dlg.setMessage(String.valueOf(values[0]));
		}

	}

	/**
	 * @author zhaohai 一个用来保存资源文件的线程
	 */
	class SaveFileTask extends AsyncTask<String, String, String> {
		// 进度条
		private ProgressDialog dlg;

		// 执行耗时任务
		@Override
		protected String doInBackground(String... params) {
			try {
				writeELFString((String) params[0]);
			} catch (IOException e) {
				e.printStackTrace();
				return e.toString();
			}
			return getString(R.string.success);
		}

		// 耗时任务执行完毕后的事件处理
		@Override
		protected void onPostExecute(String result) {
			// 隐藏进度条
			dlg.dismiss();
			// 如果返回的结果不是成功
			if (!result.equals(getString(R.string.success))) {
				// 显示错误信息
				showMessage(MainActivity.this, result).show();
				return;
			}
			Toast.makeText(MainActivity.this, "success",0).show();
			finish();
		}

		// 耗时任务开始前执行的任务
		@Override
		protected void onPreExecute() {
			super.onPreExecute();
			// 初始化进度条
			dlg = new ProgressDialog(MainActivity.this);
			// 设置进度条标题
			dlg.setTitle(R.string.saving);
			// 设置按进度条外部进度条不消失
			dlg.setCancelable(false);
			// 显示进度条
			dlg.show();
		}

	}

	// 数据适配器
	public class stringListAdapter extends BaseAdapter {

		// 上下文
		private Context mContext;

		// 构造函数
		public stringListAdapter(Context context) {
			super();
			// 获取上下文
			this.mContext = context;
		}

		// 获取数据成员个数
		@Override
		public int getCount() {
			// TODO Auto-generated method stub
			return txtOriginal.size();
		}

		// 获取指定条目的内容
		@Override
		public Object getItem(int arg0) {
			// TODO Auto-generated method stub
			return arg0;
		}

		// 获取指定条目的文字
		@Override
		public long getItemId(int arg0) {
			// TODO Auto-generated method stub
			return arg0;
		}

		// 获取View
		@SuppressLint({ "ViewHolder", "InflateParams" })
		@Override
		public View getView(final int position, View view, ViewGroup arg2) {

			// 文本框内容改变的事件监听器
			TextWatcher textWatcher = new TextWatcher() {

				// 文本改变后的事件处理
				@Override
				public void afterTextChanged(Editable s) {
					// 向当前位置添加新的内容，以此实现文本的更新
					txtTranslated.set(position, s.toString());
					isChanged = true;
				}

				// 文本改变之前的事件处理
				@Override
				public void beforeTextChanged(CharSequence s, int start, int count, int after) {
				}

				// 文本改变的事件处理
				@Override
				public void onTextChanged(CharSequence s, int start, int before, int count) {

				}

			};

			// 创建view对象
			view = LayoutInflater.from(mContext).inflate(R.layout.res_string_item, null);
			// 获取显示原来的字符串的控件
			final TextView txtOriginalView = (TextView) view.findViewById(R.id.txtOriginal);
			// 获取用来修改的文本框
			EditText txtTranslatedView = (EditText) view.findViewById(R.id.txtTranslated);

			final String originalStr = txtOriginal.get(position);
			// 显示原来的字符串
			txtOriginalView.setText(originalStr);
			// 显示修改后的字符串
			txtTranslatedView.setText(txtTranslated.get(position));
			txtTranslatedView.setFilters(new InputFilter[] { new InputFilter() {
				@Override
				public CharSequence filter(CharSequence source, int start, int end, Spanned dest, int dstart,
						int dend) {
					int len = 0;
					boolean more = false;
					do {
						SpannableStringBuilder builder = new SpannableStringBuilder(dest).replace(dstart, dend,
								source.subSequence(start, end));
						len = builder.toString().getBytes().length;
						more = len > originalStr.getBytes().length;
						if (more) {
							end--;
							source = source.subSequence(start, end);
						}
					} while (more);
					return source;
				}
			}});
			// 为文本框设置内容改变的监听器
			txtTranslatedView.addTextChangedListener(textWatcher);
			View.OnLongClickListener longclick_listener = new View.OnLongClickListener() {
				@Override
				public boolean onLongClick(View v) {
					ClipboardManager cm = (ClipboardManager) mContext.getSystemService(Context.CLIPBOARD_SERVICE);
					// 将文本内容放到系统剪贴板里。
					cm.setText(txtOriginalView.getText());
					Toast.makeText(MainActivity.this, "复制成功", Toast.LENGTH_LONG).show();
					return true;
				}
			};
			txtOriginalView.setOnLongClickListener(longclick_listener);
			return view;
		}
	}

	// 存储资源种类的集合
	public static List<String> Types;

	/**
	 * 
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public static byte[] InputStream2ByteArray(InputStream is) throws IOException {
		int count;
		byte[] buffer = new byte[2048];
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		while ((count = is.read(buffer)) != -1) {
			bos.write(buffer, 0, count);
		}
		bos.close();
		return bos.toByteArray();
	}

	// 显示信息的方法
	public static AlertDialog.Builder showMessage(Context activity, String message) {
		return new AlertDialog.Builder(activity).setMessage(message).setNegativeButton(R.string.ok, null)
				.setCancelable(false).setTitle(R.string.error);
	}

	private String fileSrc;

	private Elf elfParser;

	// 存储字符串的集合
	public List<String> txtOriginal = new ArrayList<String>();

	// 存储修改后的字符串的集合
	public List<String> txtTranslated = new ArrayList<String>();

	// 列表控件
	public ListView stringListView;

	// 数据处理器
	public stringListAdapter mAdapter;

	// 存储资源的集合
	private Map<String, ResourceHelper> RESOURCES;

	// 显示资源种类的文本控件
	private TextView textCategory;

	// 字符串是否修改
	public boolean isChanged;

	/**
	 * 文本框内容改变的事件监听器
	 * 
	 * @author zhaohai
	 */
	private TextWatcher textWatcher = new TextWatcher() {

		// 文本改变后的事件处理
		@Override
		public void afterTextChanged(Editable s) {
			// 初始化一个线程用来获取资源
			AsyncTask<String, Void, Void> task = new GetTask();
			// 开启该线程
			task.execute(textCategory.getText().toString());
		}

		// 文本改变之前的事件处理
		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			// TODO Auto-generated method stub
		}

		// 文本改变的事件处理
		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {

		}

	};

	// 一些控件的点击事件监听器
	private OnClickListener MyOnClickListener = new OnClickListener() {
		@Override
		public void onClick(View arg0) {
			// TODO Auto-generated method stub
			switch (arg0.getId()) {
			// 点击了资源类型的文本框
			case R.id.textCategory:
				// 弹出一个对话框，列出所有的资源类型
				new AlertDialog.Builder(MainActivity.this).setTitle("")
						.setItems(Types.toArray(new String[Types.size()]), new DialogInterface.OnClickListener() {
							// 对话框上的条目点击的事件监听器
							@Override
							public void onClick(DialogInterface arg0, int arg1) {
								// TODO Auto-generated method stub
								textCategory.setText(Types.get(arg1));
							}
						}).create().show();
				break;
			}
		}
	};

	private boolean checkChanged() {
		for (String str : txtTranslated) {
			if (!str.equals("")) {
				return true;
			}
		}
		return false;
	}

	/** 初始化容器 **/
	private void initList() {
		for (int i = 0; i < txtOriginal.size(); i++) {
			// 向储存修改后的字符串的列表中添加空成员
			txtTranslated.add("");
		}
	}

	/** 根据返回选择的文件，来进行操作 **/
	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent data) {
		// TODO Auto-generated method stub
		if (resultCode == Activity.RESULT_OK) {
			Uri uri = data.getData();
			fileSrc = uri.getPath();
			try {
				open(new FileInputStream(fileSrc));
			} catch (IOException e) {
				showMessage(this, e.toString()).show();
			}
		}
		super.onActivityResult(requestCode, resultCode, data);
	}

	/** 返回事件 */
	@Override
	public void onBackPressed() {
		if (isChanged || checkChanged()) { // 保存文件
			showSaveDialog();
		} else {
			finish();
		}
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// 设置主界面布局文件
		setContentView(R.layout.string_list);
		// 初始化列表控件
		stringListView = (ListView) findViewById(R.id.list_res_string);
		// 初始化显示资源类型的文本框
		textCategory = (TextView) findViewById(R.id.textCategory);
		// 为显示资源类型的文本框设置点击事件的监听器
		textCategory.setOnClickListener(MyOnClickListener);
		// 为显示资源类型的文本框设置文本内容改变的监听器
		textCategory.addTextChangedListener(textWatcher);
		// 初始化数据适配器
		mAdapter = new stringListAdapter(this);
		// 为列表控件设置数据适配器
		stringListView.setAdapter(mAdapter);
		this.OpenSystemFile();
	}

	private void open(InputStream resInputStream) {
		// 初始化一个线程用来解析资源文件
		AsyncTask<InputStream, Integer, String> task = new ParseTask();
		try {
			// 开启该线程
			task.execute(resInputStream);
		} catch (OutOfMemoryError e) {
			showMessage(this, getString(R.string.out_of_memory)).show();
		}
		// 初始化一个线程用来获取解析后的资源
		AsyncTask<String, Void, Void> getTask = new GetTask();
		// 开启该线程
		getTask.execute(textCategory.getText().toString());
	}

	public void OpenSystemFile() {
		Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
		intent.setType("*/*");
		intent.addCategory(Intent.CATEGORY_OPENABLE);
		try {
			startActivityForResult(Intent.createChooser(intent, "请选择文件!"), 0x111);
		} catch (android.content.ActivityNotFoundException ex) {
			// Potentially direct the user to the Market with a Dialog
			Toast.makeText(this, "请安装文件管理器", Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * ELF解析器
	 * 
	 * @param result
	 *            用来存放结果
	 * @param is
	 *            文件输入流
	 **/
	public void parseELF(ResourceCallBack callBack, InputStream is)
			throws UnknownFormatConversionException, IOException {
		elfParser = new Elf(new ByteArrayInputStream(InputStream2ByteArray(is)), callBack);
	}

	/** 显示保存文件的对话框 **/
	private void showSaveDialog() {
		new AlertDialog.Builder(this).setTitle(R.string.notice).setMessage(R.string.ensure_save)
				.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						File file = new File(fileSrc);
						File bak = new File(fileSrc + ".bak");
						file.renameTo(bak);
						file.delete();
						SaveFileTask saveTask = new SaveFileTask();
						saveTask.execute(fileSrc);
					}
				}).setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						setResult(Activity.RESULT_CANCELED, getIntent());
						finish();
					}
				}).create().show();
	}

	// 保存ELF字符串
	@SuppressLint("DefaultLocale")
	public void writeELFString(String output) throws IOException {
		// 整理RoData
		if (textCategory.getText().toString().equals("rodata")) {
			elfParser.sortStrData(txtOriginal, txtTranslated, elfParser.ro_items);
		} else { // 整理Dynstr
			elfParser.sortStrData(txtOriginal, txtTranslated, elfParser.dy_items);
		}
		OutputStream fos = new FileOutputStream(output);
		elfParser.writeELF(fos);
		fos.close();
	}
}
