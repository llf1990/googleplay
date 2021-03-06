package com.googleplay.application.utils;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.security.PublicKey;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.interfaces.DSAPublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by mwqi on 2014/6/19.
 */
public class PackageUtils {

	/** 根据packageName获取packageInfo */
	public static PackageInfo getPackageInfo(String packageName){
		Context context = UIUtils.getContext();
		if(null == context){
			return null;
		}
		if(StringUtils.isEmpty(packageName)){
			packageName = context.getPackageName();
		}
		PackageInfo info = null;
		PackageManager manager = context.getPackageManager();
		//根据packagename获取packageinfo
		try {
			info = manager.getPackageInfo(packageName, PackageManager.GET_UNINSTALLED_PACKAGES);
			
		} catch (Exception e) {
			LogUtils.e(e);
		}
		return info;
	}
	/**
	 * 获取本应用的versioncode
	 * @return
	 */
	public static int getVersionCode(){
		PackageInfo info = getPackageInfo(null);
		if(null == info){
			return -1;
		}else{
			return info.versionCode;
		}
	}
	/**
	 * 获取本应用的版本名称
	 * @return
	 */
	public static String getVersionName(){
		PackageInfo info = getPackageInfo(null);
		if(null != info){
			return info.packageName;
		}else{
			return null;
		}
	}
	/**
	 * 判断是否第三方应用软件
	 * @param packageName
	 * @return
	 */
	public static boolean isThirdPartyApp(String packageName){
		Context context = UIUtils.getContext();
		if(null == context){
			return false;
		}
		PackageManager pmanager = context.getPackageManager();
		PackageInfo info ;
		try {
			 info = pmanager.getPackageInfo(packageName,0);
			 return isThirdPartyApp(info);
		} catch (NameNotFoundException e) {
			LogUtils.e(e);
			return false;
		}
	}
	/**
	 * 是否第三方应用
	 * @param info
	 * @return
	 */
	public static boolean isThirdPartyApp(PackageInfo info ){
		if(null == info||null == info.applicationInfo){
			return false;
		}
		return (info.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM) == 0 ||
		((info.applicationInfo.flags&ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0);
	}
	/**
	 * 读取指定路径下apk文件签名
	 * @param filePath
	 * @return
	 */
	public static String getJarSignature(String filePath){
		if(null == filePath){
			return null;
		}
		String resultSign = "";
		String resultKey = "";
		List<ZipEntry> names = new ArrayList<ZipEntry>();
		try {
			ZipFile zf = new ZipFile(filePath);
			Enumeration<ZipEntry> zi = (Enumeration<ZipEntry>) zf.entries();
			while(zi.hasMoreElements()){
				ZipEntry zipE = zi.nextElement();
				String name = zipE.getName();
				if(name.startsWith("META-INF/")&&(name.endsWith(".RSA")||name.endsWith(".DSA"))){
					names.add(zipE);
				}
			}
			Collections.sort(names,new Comparator<ZipEntry>(){

				@Override
				public int compare(ZipEntry lhs, ZipEntry rhs) {
					if(null != lhs && null!= rhs){
						return lhs.getName().compareToIgnoreCase(rhs.getName());
					}
					return 0;
				}
			});
			for(ZipEntry ze : names){
				InputStream is = zf.getInputStream(ze);
				try {
					CertificateFactory cf = CertificateFactory.getInstance("X.509");
					CertPath cp = cf.generateCertPath(is,"PKCS7");
					List<?> list = cp.getCertificates();
					for(Object obj : list){
						if(!(obj instanceof X509Certificate))
							continue;
						X509Certificate cert = (X509Certificate) obj;
						StringBuilder builder = new StringBuilder();
						builder.setLength(0);
						byte[] key = getPKBytes(cert.getPublicKey());
						for(byte ktemp : key){
							builder.append(String.format("%02X", ktemp));
						}
						resultKey += builder.toString();
						builder.setLength(0);
						byte[] signature = cert.getSignature();
						for(byte sign: signature){
							builder.append(String.format("%02X", sign));
						}
						resultSign += builder.toString();
					}
				} catch (Exception e) {
					LogUtils.e(e);
				}
				IOUtils.close(is);
			}
			if(!StringUtils.isEmpty(resultKey)&&StringUtils.isEmpty(resultSign)){
				return hashCode(resultKey)+","+hashCode(resultSign);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	/**
	 * 根据公钥获取key
	 * @param pk
	 * @return
	 */
	private static byte[] getPKBytes(PublicKey pk){
		if(pk instanceof RSAPublicKey){
			RSAPublicKey rsaK = (RSAPublicKey) pk;
			return rsaK.getModulus().toByteArray();
		}else if(pk instanceof DSAPublicKey){
			DSAPublicKey dsaK = (DSAPublicKey) pk;
			return dsaK.getY().toByteArray();
		}
		return null;
	}
	/**
	 * 计算签名是的hashcode算法
	 * @param str
	 * @return
	 */
	public static int hashCode(String str){
		int hash = 0;
		if(null != str){
			int multiplier = 1;
			int offset = 0;
			int count = str.length();
			char[] value = new char[count];
			str.getChars(offset, count, value, 0);
			for(int i = offset+count-1; i >= offset ; i--){
				hash += value[i]*multiplier;
				int shifted = multiplier << 5;
				multiplier = shifted - multiplier;
			}
		}
		return hash;
	}
	/**
	 * 通过包名读取已安装APP数字签名
	 * @param packageName
	 * @return
	 */
	public static String getInstalledPackageSignature(String packageName){
		Context context = UIUtils.getContext();
		if(null == context){
			return null;
		}
		String signature = null;
		try {
			PackageManager pm = context.getPackageManager();
			ApplicationInfo appinfo = pm.getApplicationInfo(packageName, PackageManager.GET_SIGNATURES);
			String apkPath = appinfo.sourceDir;
			signature = getJarSignature(apkPath);
		} catch (Exception e) {
			LogUtils.e(e);
		}
		return signature;
	}
	/**
	 * 获取指定路径的apk资源
	 * @param apkPath
	 * @return
	 */
	@SuppressWarnings({ "rawtypes", "unchecked" })
	public static Resources getAPKResource(String apkPath) throws Exception{
		Context context = UIUtils.getContext();
		if(null == context){
			return null;
		}
		String PathAssetManager = "android.content.res.AssetManager";
		Class assetMagCls = Class.forName(PathAssetManager);
		Constructor assetMagCt = assetMagCls.getConstructor((Class[])null);
		Object assetMag  = assetMagCt.newInstance((Object[])null);
		Class[] typeArgs = new Class[1];
		typeArgs[0] = String.class;
		Method assetMagAddAssetPathMtd = assetMagCls.getDeclaredMethod("addAssetPath",typeArgs);
		Object[] valuesArgs = new Object[1];
		valuesArgs[0] = apkPath;
		assetMagAddAssetPathMtd.invoke(assetMag, valuesArgs);
		Resources res = context.getResources();
		typeArgs = new Class[3];
		typeArgs[0] = assetMag.getClass();
		typeArgs[1] = res.getDisplayMetrics().getClass();
		typeArgs[2] = res.getConfiguration().getClass();
		Constructor resCt = Resources.class.getConstructor(typeArgs);
		valuesArgs = new Object[3];
		valuesArgs[0] = assetMag;
		valuesArgs[1] = res.getDisplayMetrics();
		valuesArgs[2] = res.getConfiguration();
		res  = (Resources)resCt.newInstance(valuesArgs);
		return res;
	}
	
 }
