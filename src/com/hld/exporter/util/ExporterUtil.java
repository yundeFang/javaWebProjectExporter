package com.hld.exporter.util;

import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.javaee.web.WebRoot;
import com.intellij.javaee.web.facet.WebFacet;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @Description
 * @Author Harland.Fang
 * @Date 2019/3/12
 **/
public class ExporterUtil {

    /**
     * 是否是JAVA 文件
     */
    public static boolean isJavaFile(VirtualFile file) {
        String fileName = file.getName();
        if (fileName.endsWith(".java")) return true;
        return false;
    }

    /**
     * 获取依赖的jar路径列表
     */
    public static String getJars(Module module) {
        StringBuffer buffer = new StringBuffer();
        VirtualFile[] allJars = ModuleRootManager.getInstance(module).orderEntries().classes().getRoots();
        ModuleRootManager.getInstance(module).getSdk();
        for (VirtualFile file : allJars) {
            buffer.append(file.getPath())
                    .append(";");
        }
        String jars = buffer.toString();
        jars = jars.replace("!/", "")
                .replace("jar://", "");
        return jars;
    }


    /**
     * 判断文件是否来自某些目录下
     */
    public static VirtualFile getSource4File(List<VirtualFile> sourceRoots, List<VirtualFile> resourceRoots, VirtualFile file, Project project) {
        VirtualFile fileSourceBelong = ProjectRootManager.getInstance(project).getFileIndex().getSourceRootForFile(file);
        if (null == fileSourceBelong)
            return null;
        for (VirtualFile sourceFile : sourceRoots) {
            if (sourceFile.equals(fileSourceBelong)) return sourceFile;
        }
        for (VirtualFile sourceFile : resourceRoots) {
            if (sourceFile.equals(fileSourceBelong)) return sourceFile;
        }
        return null;
    }

    /**
     * 创建目录
     */
    public static void mkdirs(String path) {
        File filePath = new File(path);
        if ( filePath.exists() )
            filePath.delete();
        filePath.mkdirs();
    }

    /**
     * 获取webRoot路径
     */
    public static List<WebRoot> getWebRootUrl(Module module) {
        Facet[] facets = FacetManager.getInstance(module).getAllFacets();
        for (Facet facet : facets) {
            if ( "Web".equals(facet.getName()) ) {
                WebFacet webFacet = (WebFacet) facet;
                return webFacet.getWebRoots();
            }
        }
        return new ArrayList<>();
    }

//    /**
//     * 是否是webRoot下的文件
//     */
//    public static String getWebRoot4File(List<WebRoot> webRoots, VirtualFile file){
//        String webRootPath;
//        for( WebRoot webRoot : webRoots ) {
//            webRootPath = webRoot.getDirectoryUrl();
//            if ( 0 == file.getPath().indexOf(webRootPath) )
//                return webRootPath;
//        }
//        return "";
//    }

    public static List<VirtualFile> getAllFiles(List<VirtualFile> files){
        List<VirtualFile> list = new ArrayList<>();
        for ( VirtualFile rootFile : files ){
            list.addAll(getAllFile(rootFile));
        }
        return list;
    }

    private static List<VirtualFile> getAllFile(VirtualFile rootFile) {
        List<VirtualFile> list = new ArrayList<>();
        if ( !rootFile.isDirectory() ) {
            list.add(rootFile);
            return list;
        }

        VirtualFile[] files = rootFile.getChildren();
        for ( VirtualFile file : files ) {
            if ( file.isDirectory() )
                list.addAll(getAllFile(file));
            else
                list.add(file);
        }
        return list;
    }
}
