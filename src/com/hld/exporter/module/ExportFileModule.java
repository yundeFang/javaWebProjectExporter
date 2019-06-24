package com.hld.exporter.module;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * @Description
 * @Author fangyunde
 * @Date 2019/3/8
 **/
public class ExportFileModule {
    private String path;
    private VirtualFile file;
    private Module module;

    public VirtualFile getFile() {
        return file;
    }

    public ExportFileModule(String path, VirtualFile file, Module module) {
        this.path = path;
        this.file = file;
        this.module = module;
    }
}
