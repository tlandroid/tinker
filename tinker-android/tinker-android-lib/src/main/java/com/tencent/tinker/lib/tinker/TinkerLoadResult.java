/*
 * Copyright (C) 2016 Tencent WeChat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.tinker.lib.tinker;

import android.content.Context;
import android.content.Intent;

import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.ShareIntentUtil;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.SharePatchInfo;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;
import java.util.HashMap;

/**
 * Created by zhangshaowen on 16/3/25.
 */
public class TinkerLoadResult {
    private static final String TAG = "TinkerLoadResult";
    //@Nullable
    public SharePatchInfo patchInfo;
    //@Nullable
    public String         currentVersion;

    public boolean                 versionChanged;
    //@Nullable
    public File                    patchVersionDirectory;
    //@Nullable
    public File                    patchVersionFile;
    //@Nullable
    public File                    dexDirectory;
    //@Nullable
    public File                    libraryDirectory;
    //@Nullable
    public HashMap<String, String> dexes;
    //@Nullable
    public HashMap<String, String> libs;
    //@Nullable
    public HashMap<String, String> packageConfig;

    public int loadCode;

    public long costTime;

    public boolean parseTinkerResult(Context context, Intent intentResult) {
        Tinker tinker = Tinker.with(context);
        loadCode = ShareIntentUtil.getIntentReturnCode(intentResult);
        TinkerLog.i(TAG, "parseTinkerResult loadCode:%d", loadCode);

        costTime = ShareIntentUtil.getIntentPatchCostTime(intentResult);
        //@Nullable
        final String oldVersion = ShareIntentUtil.getStringExtra(intentResult, ShareIntentUtil.INTENT_PATCH_OLD_VERSION);
        //@Nullable
        final String newVersion = ShareIntentUtil.getStringExtra(intentResult, ShareIntentUtil.INTENT_PATCH_NEW_VERSION);

        final File patchDirectory = tinker.getPatchDirectory();
        final File patchInfoFile = tinker.getPatchInfoFile();

        final boolean isMainProcess = tinker.isMainProcess();


        if (oldVersion != null && newVersion != null) {
            if (isMainProcess) {
                currentVersion = newVersion;
            } else {
                currentVersion = oldVersion;
            }

            TinkerLog.d(TAG, "parseTinkerResult oldVersion:%s, newVersion:%s, current:%s", oldVersion, newVersion,
                currentVersion);
            //current version may be nil
            String patchName = SharePatchFileUtil.getPatchVersionDirectory(currentVersion);
            if (!ShareTinkerInternals.isNullOrNil(patchName)) {
                patchVersionDirectory = new File(patchDirectory.getAbsolutePath() + "/" + patchName);
                patchVersionFile = new File(patchVersionDirectory.getAbsolutePath(), SharePatchFileUtil.getPatchVersionFile(currentVersion));
                dexDirectory = new File(patchVersionDirectory, ShareConstants.DEX_PATH);
                libraryDirectory = new File(patchVersionDirectory, ShareConstants.SO_PATH);
            }
            patchInfo = new SharePatchInfo(oldVersion, newVersion);
            versionChanged = !(oldVersion.equals(newVersion));
        }

        //found uncaught exception, just return
        Throwable exception = ShareIntentUtil.getIntentPatchException(intentResult);
        if (exception != null) {
            loadCode = ShareConstants.ERROR_LOAD_PATCH_EXCEPTION;
            TinkerLog.i(TAG, "Tinker load have exception loadCode:%d", loadCode);
            tinker.getLoadReporter().onLoadException(exception);
            return false;
        }

        switch (loadCode) {
            case ShareConstants.ERROR_LOAD_GET_INTENT_FAIL:
                TinkerLog.e(TAG, "can't get the right intent return code");
                throw new TinkerRuntimeException("can't get the right intent return code");
//                    break;
            case ShareConstants.ERROR_LOAD_DISABLE:
                TinkerLog.w(TAG, "tinker is disable, just return");
                break;
//            case ShareConstants.ERROR_LOAD_PATCH_NOT_SUPPORTED:
//                TinkerLog.w(TAG, "tinker is not supported, just return");
//                break;
            case ShareConstants.ERROR_LOAD_PATCH_DIRECTORY_NOT_EXIST:
            case ShareConstants.ERROR_LOAD_PATCH_INFO_NOT_EXIST:
                TinkerLog.w(TAG, "can't find patch file, is ok, just return");
                break;

            case ShareConstants.ERROR_LOAD_PATCH_INFO_CORRUPTED:
                TinkerLog.e(TAG, "path info corrupted");
                tinker.getLoadReporter().onLoadPatchInfoCorrupted(oldVersion, newVersion, patchInfoFile);
                break;

            case ShareConstants.ERROR_LOAD_PATCH_INFO_BLANK:
                TinkerLog.e(TAG, "path info blank, wait main process to restart");
                break;

            case ShareConstants.ERROR_LOAD_PATCH_VERSION_DIRECTORY_NOT_EXIST:
                TinkerLog.e(TAG, "patch version directory not found, current version:%s", currentVersion);
                tinker.getLoadReporter().onLoadFileNotFound(patchVersionDirectory,
                    ShareConstants.TYPE_PATCH_FILE, true);
                break;

            case ShareConstants.ERROR_LOAD_PATCH_VERSION_FILE_NOT_EXIST:
                TinkerLog.e(TAG, "patch version file not found, current version:%s", currentVersion);
                if (patchVersionFile == null) {
                    throw new TinkerRuntimeException("error load patch version file not exist, but file is null");
                }
                tinker.getLoadReporter().onLoadFileNotFound(patchVersionFile,
                    ShareConstants.TYPE_PATCH_FILE, false);
                break;

            case ShareConstants.ERROR_LOAD_PATCH_PACKAGE_CHECK_FAIL:
                TinkerLog.i(TAG, "patch package check fail");
                if (patchVersionFile == null) {
                    throw new TinkerRuntimeException("error patch package check fail , but file is null");
                }
                int errorCode = intentResult.getIntExtra(ShareIntentUtil.INTENT_PATCH_PACKAGE_PATCH_CHECK, ShareConstants.ERROR_LOAD_GET_INTENT_FAIL);
                tinker.getLoadReporter().onLoadPackageCheckFail(patchVersionFile, errorCode);
                break;
            case ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_DIRECTORY_NOT_EXIST:

                if (dexDirectory != null) {
                    TinkerLog.e(TAG, "patch dex file directory not found:%s", dexDirectory.getAbsolutePath());
                    tinker.getLoadReporter().onLoadFileNotFound(dexDirectory,
                        ShareConstants.TYPE_DEX, true);
                } else {
                    //should be not here
                    TinkerLog.e(TAG, "patch dex file directory not found, warning why the path is null!!!!");
                    throw new TinkerRuntimeException("patch dex file directory not found, warning why the path is null!!!!");
//                        tinker.getApplyReporter().onLoadFileNotFound(null,
//                            ShareConstants.TYPE_DEX, true);
                }

                break;
            case ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_FILE_NOT_EXIST:
                String dexPath = ShareIntentUtil.getStringExtra(intentResult,
                    ShareIntentUtil.INTENT_PATCH_MISSING_DEX_PATH);
                if (dexPath != null) {
                    //we only pass one missing file
                    TinkerLog.e(TAG, "patch dex file not found:%s", dexPath);
                    tinker.getLoadReporter().onLoadFileNotFound(new File(dexPath),
                        ShareConstants.TYPE_DEX, false);

                } else {
                    TinkerLog.e(TAG, "patch dex file not found, but path is null!!!!");
                    throw new TinkerRuntimeException("patch dex file not found, but path is null!!!!");
//                        tinker.getLoadReporter().onLoadFileNotFound(null,
//                            ShareConstants.TYPE_DEX, false);
                }
                break;
            case ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_OPT_FILE_NOT_EXIST:
                String dexOptPath = ShareIntentUtil.getStringExtra(intentResult,
                    ShareIntentUtil.INTENT_PATCH_MISSING_DEX_PATH);
                if (dexOptPath != null) {
                    //we only pass one missing file
                    TinkerLog.e(TAG, "patch dex opt file not found:%s", dexOptPath);
                    tinker.getLoadReporter().onLoadFileNotFound(new File(dexOptPath),
                        ShareConstants.TYPE_DEX_OPT, false);

                } else {
                    TinkerLog.e(TAG, "patch dex opt file not found, but path is null!!!!");
                    throw new TinkerRuntimeException("patch dex opt file not found, but path is null!!!!");
//                        tinker.getLoadReporter().onLoadFileNotFound(null,
//                            ShareConstants.TYPE_DEX, false);
                }
                break;
            case ShareConstants.ERROR_LOAD_PATCH_VERSION_LIB_DIRECTORY_NOT_EXIST:
                if (patchVersionDirectory != null) {
                    TinkerLog.e(TAG, "patch lib file directory not found:%s", libraryDirectory.getAbsolutePath());
                    tinker.getLoadReporter().onLoadFileNotFound(libraryDirectory,
                        ShareConstants.TYPE_LIBRARY, true);
                } else {
                    //should be not here
                    TinkerLog.e(TAG, "patch lib file directory not found, warning why the path is null!!!!");
                    throw new TinkerRuntimeException("patch lib file directory not found, warning why the path is null!!!!");

//                        tinker.getLoadReporter().onLoadFileNotFound(null,
//                            ShareConstants.TYPE_LIBRARY, true);
                }

                break;
            case ShareConstants.ERROR_LOAD_PATCH_VERSION_LIB_FILE_NOT_EXIST:
                String libPath = ShareIntentUtil.getStringExtra(intentResult,
                    ShareIntentUtil.INTENT_PATCH_MISSING_LIB_PATH);
                if (libPath != null) {
                    //we only pass one missing file and then we break
                    TinkerLog.e(TAG, "patch lib file not found:%s", libPath);
                    tinker.getLoadReporter().onLoadFileNotFound(new File(libPath),
                        ShareConstants.TYPE_LIBRARY, false);
                } else {
                    TinkerLog.e(TAG, "patch lib file not found, but path is null!!!!");
                    throw new TinkerRuntimeException("patch lib file not found, but path is null!!!!");
//                        tinker.getLoadReporter().onLoadFileNotFound(null,
//                            ShareConstants.TYPE_LIBRARY, false);
                }
                break;

            case ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_CLASSLOADER_NULL:
                TinkerLog.e(TAG, "patch dex load fail, classloader is null");
                break;
            case ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_MD5_MISMATCH:
                String mismatchPath = ShareIntentUtil.getStringExtra(intentResult,
                    ShareIntentUtil.INTENT_PATCH_MISMATCH_DEX_PATH);
                if (mismatchPath == null) {
                    TinkerLog.e(TAG, "patch dex file md5 is mismatch, but path is null!!!!");
                    throw new TinkerRuntimeException("patch dex file md5 is mismatch, but path is null!!!!");
                } else {
                    TinkerLog.e(TAG, "patch dex file md5 is mismatch: %s", mismatchPath);
                    tinker.getLoadReporter().onLoadFileMd5Mismatch(new File(mismatchPath),
                        ShareConstants.TYPE_DEX);
                }
                break;
            case ShareConstants.ERROR_LOAD_PATCH_VERSION_DEX_LOAD_FAIL:
                TinkerLog.e(TAG, "patch dex file found exception");
                Exception e = ShareIntentUtil.getIntentPatchDexException(intentResult);
                if (e == null) {
                    throw new TinkerRuntimeException("patch dex file found exception, but exception is null!!!!");
                }
                tinker.getLoadReporter().onLoadDexException(e);
                break;
            case ShareConstants.ERROR_LOAD_PATCH_REWRITE_PATCH_INFO_FAIL:
                TinkerLog.i(TAG, "rewrite patch info file corrupted");
                tinker.getLoadReporter().onLoadPatchInfoCorrupted(oldVersion, newVersion, patchInfoFile);
                break;

            case ShareConstants.ERROR_LOAD_OK:
                TinkerLog.i(TAG, "oh yeah, tinker load all success");
                tinker.setTinkerLoaded(true);
                //get load dex
                dexes = ShareIntentUtil.getIntentPatchDexPaths(intentResult);
                libs = ShareIntentUtil.getIntentPatchLibsPaths(intentResult);

                packageConfig = ShareIntentUtil.getIntentPackageConfig(intentResult);

                if (isMainProcess && versionChanged) {
                    //change the old version to new
                    patchInfo.oldVersion = currentVersion;
                    tinker.getLoadReporter().onLoadPatchVersionChanged(oldVersion, newVersion, patchDirectory, patchVersionDirectory.getName());

                }
                return true;
        }

        return false;

    }

    /**
     * get the base tinkerId
     *
     * @return
     */
    public String getTinkerID() {
        if (packageConfig != null) {
            String tinkerId = packageConfig.get(ShareConstants.TINKER_ID);
            return tinkerId;
        }
        return null;
    }

    /**
     * get the new tinkerId
     *
     * @return
     */
    public String getNewTinkerID() {
        if (packageConfig != null) {
            String tinkerId = packageConfig.get(ShareConstants.NEW_TINKER_ID);

            return tinkerId;
        }
        return null;
    }

    /**
     * get package configs
     *
     * @param name
     * @return
     */
    public String getPackageConfigByName(String name) {
        if (packageConfig != null) {
            return packageConfig.get(name);
        }
        return null;
    }

}