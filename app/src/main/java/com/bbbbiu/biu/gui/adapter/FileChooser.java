package com.bbbbiu.biu.gui.adapter;

import java.io.File;
import java.util.Set;

/**
 * 选择文件
 * <p/>
 * Created by finalize on 7/31/16.
 */
public interface FileChooser {

    /**
     * 已选项数量
     *
     * @return 数量
     */
    public abstract int getChosenCount();


    /**
     * 获取已选文件的路径集
     *
     * @return 绝对路径集
     */
    public abstract Set<String> getChosenFiles();


    /**
     * 文件是否已被选中
     *
     * @param file file
     * @return 是否选中
     */
    public abstract boolean isFileChosen(File file);


    /**
     * 当前数据集全被选
     */
    public abstract void setFileAllChosen();


    /**
     * 清除所有已选项目
     */
    public abstract void setFileAllDismissed();
}
