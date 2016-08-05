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

import com.tencent.tinker.lib.listener.DefaultPatchListener;
import com.tencent.tinker.lib.listener.PatchListener;
import com.tencent.tinker.lib.patch.AbstractPatch;
import com.tencent.tinker.lib.patch.RepairPatch;
import com.tencent.tinker.lib.patch.UpgradePatch;
import com.tencent.tinker.lib.reporter.DefaultLoadReporter;
import com.tencent.tinker.lib.reporter.DefaultPatchReporter;
import com.tencent.tinker.lib.reporter.LoadReporter;
import com.tencent.tinker.lib.reporter.PatchReporter;
import com.tencent.tinker.lib.service.AbstractResultService;
import com.tencent.tinker.lib.service.DefaultTinkerResultService;
import com.tencent.tinker.lib.service.TinkerPatchService;
import com.tencent.tinker.lib.util.TinkerLog;
import com.tencent.tinker.lib.util.TinkerServiceInternals;
import com.tencent.tinker.loader.TinkerRuntimeException;
import com.tencent.tinker.loader.shareutil.ShareConstants;
import com.tencent.tinker.loader.shareutil.SharePatchFileUtil;
import com.tencent.tinker.loader.shareutil.ShareTinkerInternals;

import java.io.File;

/**
 * Created by zhangshaowen on 16/3/10.
 */
public class Tinker {
    private static final String TAG = "Tinker";

    private static Tinker        sInstance;
    final          Context       context;
    /**
     * data dir, such as /data/data/tinker.sample.android/tinker
     */
    final          File          patchDirectory;
    final          PatchListener listener;
    final          LoadReporter  loadReporter;
    final          PatchReporter patchReporter;
    final          File          patchInfoFile;
    final          boolean       isMainProcess;
    final          boolean       isPatchProcess;

    /**
     * same with {@code TinkerApplication.tinkerLoadVerifyFlag}
     */
    final boolean tinkerLoadVerifyFlag;
    /**
     * same with {@code TinkerApplication.tinkerFlags}
     */
    int              tinkerFlags;
    TinkerLoadResult tinkerLoadResult;
    /**
     * whether load patch success
     */
    private        boolean loaded    = false;
    private static boolean installed = false;

    private Tinker(Context context, int tinkerFlags, LoadReporter loadReporter, PatchReporter patchReporter,
                   PatchListener listener, File patchDirectory, File patchInfoFile,
                   boolean isInMainProc, boolean isPatchProcess, boolean tinkerLoadVerifyFlag) {
        this.context = context;
        this.listener = listener;
        this.loadReporter = loadReporter;
        this.patchReporter = patchReporter;
        this.tinkerFlags = tinkerFlags;
        this.patchDirectory = patchDirectory;
        this.patchInfoFile = patchInfoFile;
        this.isMainProcess = isInMainProc;
        this.tinkerLoadVerifyFlag = tinkerLoadVerifyFlag;
        this.isPatchProcess = isPatchProcess;
    }

    /**
     * init with default config tinker
     * for safer, you must use @{link TinkerInstaller.install} first!
     *
     * @param context we will use the application context
     * @return the Tinker object
     */
    public static Tinker with(Context context) {
        if (!installed) {
            throw new TinkerRuntimeException("you must install tinker before get tinker sInstance");
        }
        if (sInstance == null) {
            synchronized (Tinker.class) {
                if (sInstance == null) {
                    sInstance = new Builder(context).build();
                }
            }
        }
        return sInstance;
    }

    /**
     * create custom tinker by {@link Tinker.Builder}
     * please do it when very first your app start.
     *
     * @param tinker
     */
    public static void create(Tinker tinker) {
        if (sInstance != null) {
            throw new TinkerRuntimeException("Tinker instance is already set.");
        }
        sInstance = tinker;
    }

    /**
     * you must install tinker first!!
     *
     * @param intentResult
     * @param serviceClass
     * @param upgradePatch
     * @param repairPatch
     */
    public void install(Intent intentResult, Class<? extends AbstractResultService> serviceClass,
                        AbstractPatch upgradePatch, AbstractPatch repairPatch
    ) {
        installed = true;
        AbstractResultService.setResultServiceClass(serviceClass);
        TinkerPatchService.setPatchProcessor(upgradePatch, repairPatch);

        if (!isTinkerEnabled()) {
            TinkerLog.e(TAG, "tinker is disabled");
            return;
        }
        if (intentResult == null) {
            throw new TinkerRuntimeException("intentResult must not be null.");
        }
        tinkerLoadResult = new TinkerLoadResult();
        tinkerLoadResult.parseTinkerResult(getContext(), intentResult);
        //after load code set
        loadReporter.onLoadResult(patchDirectory, tinkerLoadResult.loadCode, tinkerLoadResult.costTime);

        if (!loaded) {
            TinkerLog.w(TAG, "tinker load fail!");
        }
    }

    /**
     * set tinkerPatchServiceNotificationId
     * @param id
     */
    public void setPatchServiceNotificationId(int id) {
        TinkerPatchService.setTinkerNotificationId(id);
    }


    /**
     * Nullable, should check the loaded flag first
     */
    public TinkerLoadResult getTinkerLoadResultIfPresent() {
        return tinkerLoadResult;
    }

    public void install(Intent intentResult) {
        install(intentResult, DefaultTinkerResultService.class, new UpgradePatch(), new RepairPatch());
    }

    public Context getContext() {
        return context;
    }

    public boolean isMainProcess() {
        return isMainProcess;
    }

    public boolean isPatchProcess() {
        return isPatchProcess;
    }

    public void setTinkerDisable() {
        tinkerFlags = ShareConstants.TINKER_DISABLE;
    }

    public LoadReporter getLoadReporter() {
        return loadReporter;
    }

    public PatchReporter getPatchReporter() {
        return patchReporter;
    }


    public boolean isTinkerEnabled() {
        return ShareTinkerInternals.isTinkerEnabled(tinkerFlags);
    }

    public boolean isTinkerLoaded() {
        return loaded;
    }

    public boolean isTinkerInstalled() {
        return installed;
    }

    public void setTinkerLoaded(boolean isLoaded) {
        loaded = isLoaded;
    }

    public boolean isTinkerLoadVerify() {
        return tinkerLoadVerifyFlag;
    }

    public boolean isEnabledForDex() {
        return ShareTinkerInternals.isTinkerEnabledForDex(tinkerFlags);
    }

    public boolean isEnabledForNativeLib() {
        return ShareTinkerInternals.isTinkerEnabledForNativeLib(tinkerFlags);
    }

    public File getPatchDirectory() {
        return patchDirectory;
    }

    public File getPatchInfoFile() {
        return patchInfoFile;
    }

    public PatchListener getPatchListener() {
        return listener;
    }

    /**
     * clean all patch files
     */
    public void cleanPatch() {
        if (patchDirectory == null) {
            return;
        }
        SharePatchFileUtil.deleteDir(patchDirectory);
    }

    /**
     * clean the patch version files, such as tinker/patch-641e634c
     *
     * @param versionName
     */
    public void cleanPatchByVersion(String versionName) {
        if (patchDirectory == null || versionName == null) {
            return;
        }
        String path = patchDirectory.getAbsolutePath() + "/" + versionName;
        SharePatchFileUtil.deleteDir(path);
    }

    /**
     * get the rom size of tinker, use kb
     *
     * @return
     */
    public long getTinkerRomSpace() {
        if (patchDirectory == null) {
            return 0;
        }

        return SharePatchFileUtil.getDirectorySize(patchDirectory) / 1024;
    }

    /**
     * try delete the temp version files
     *
     * @param patchFile
     */
    public void cleanPatchByVersion(File patchFile) {
        if (patchDirectory == null || patchFile == null || !patchFile.exists()) {
            return;
        }
        String versionName = SharePatchFileUtil.getPatchVersionDirectory(SharePatchFileUtil.getMD5(patchFile));
        cleanPatchByVersion(versionName);
    }


    public static class Builder {
        private final Context context;
        private final boolean mainProcess;
        private final boolean patchProcess;

        private int status = -1;
        private LoadReporter  loadReporter;
        private PatchReporter patchReporter;
        private PatchListener listener;
        private File          patchDirectory;
        private File          patchInfoFile;
        private Boolean       tinkerLoadVerifyFlag;

        /**
         * Start building a new {@link Tinker} instance.
         */
        public Builder(Context context) {
            if (context == null) {
                throw new TinkerRuntimeException("Context must not be null.");
            }
            this.context = context;
            this.mainProcess = TinkerServiceInternals.isInMainProcess(context);
            this.patchProcess = TinkerServiceInternals.isInTinkerPatchServiceProcess(context);
            this.patchDirectory = SharePatchFileUtil.getPatchDirectory(context);
            this.patchInfoFile = SharePatchFileUtil.getPatchInfoFile(patchDirectory.getAbsolutePath());
            TinkerLog.w(TAG, "tinker patch directory: %s", patchDirectory);
        }

        public Builder tinkerFlags(int tinkerFlags) {
            if (this.status != -1) {
                throw new TinkerRuntimeException("tinkerFlag is already set.");
            }
            this.status = tinkerFlags;
            return this;
        }

        public Builder tinkerLoadVerifyFlag(Boolean verifyMd5WhenLoad) {
            if (verifyMd5WhenLoad == null) {
                throw new TinkerRuntimeException("tinkerLoadVerifyFlag must not be null.");
            }
            if (this.tinkerLoadVerifyFlag != null) {
                throw new TinkerRuntimeException("tinkerLoadVerifyFlag is already set.");
            }
            this.tinkerLoadVerifyFlag = verifyMd5WhenLoad;
            return this;
        }

        public Builder loadReport(LoadReporter loadReporter) {
            if (loadReporter == null) {
                throw new TinkerRuntimeException("loadReporter must not be null.");
            }
            if (this.loadReporter != null) {
                throw new TinkerRuntimeException("loadReporter is already set.");
            }
            this.loadReporter = loadReporter;
            return this;
        }

        public Builder patchReporter(PatchReporter patchReporter) {
            if (patchReporter == null) {
                throw new TinkerRuntimeException("patchReporter must not be null.");
            }
            if (this.patchReporter != null) {
                throw new TinkerRuntimeException("patchReporter is already set.");
            }
            this.patchReporter = patchReporter;
            return this;
        }

        public Builder listener(PatchListener listener) {
            if (listener == null) {
                throw new TinkerRuntimeException("listener must not be null.");
            }
            if (this.listener != null) {
                throw new TinkerRuntimeException("listener is already set.");
            }
            this.listener = listener;
            return this;
        }

        public Tinker build() {
            if (status == -1) {
                status = ShareConstants.TINKER_ENABLE_ALL;
            }

            if (loadReporter == null) {
                loadReporter = new DefaultLoadReporter(context);
            }

            if (patchReporter == null) {
                patchReporter = new DefaultPatchReporter(context);
            }

            if (listener == null) {
                listener = new DefaultPatchListener(context);
            }

            if (tinkerLoadVerifyFlag == null) {
                tinkerLoadVerifyFlag = false;
            }

            return new Tinker(context, status, loadReporter, patchReporter, listener, patchDirectory,
                patchInfoFile, mainProcess, patchProcess, tinkerLoadVerifyFlag);
        }
    }

}