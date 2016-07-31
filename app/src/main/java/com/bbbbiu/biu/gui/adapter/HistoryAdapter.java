package com.bbbbiu.biu.gui.adapter;

import android.app.Activity;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.bbbbiu.biu.R;
import com.bbbbiu.biu.db.TransferRecord;
import com.bbbbiu.biu.gui.adapter.util.VideoIconRequestHandler;
import com.bbbbiu.biu.gui.choose.listener.OnChoosingListener;
import com.bbbbiu.biu.util.StorageUtil;
import com.raizlabs.android.dbflow.list.FlowQueryList;
import com.squareup.picasso.Picasso;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import butterknife.Bind;
import butterknife.ButterKnife;

/**
 * Created by YieldNull at 5/9/16
 */
public class HistoryAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements FileChooser {
    private static final String TAG = HistoryAdapter.class.getSimpleName();

    private static final String PICASSO_TAG = "tag-img";

    private Activity context;
    private OnChoosingListener mOnChoosingListener;


    private FlowQueryList<TransferRecord> mDataSet;

    private Picasso mVideoPicasso;
    private Picasso mImgPicasso;

    private Set<TransferRecord> mChosenFiles = new HashSet<>();

    private boolean mOnChoosing;

    public void setOnChoosing() {
        mOnChoosing = true;
        notifyDataSetChanged();
    }

    public boolean isOnChoosing() {
        return mOnChoosing;
    }

    @Override
    public int getChosenCount() {
        return mChosenFiles.size();
    }

    @Override
    public Set<String> getChosenFiles() {
        Set<String> files = new HashSet<>();
        for (TransferRecord record : mChosenFiles) {
            files.add(record.path);
        }
        return files;
    }

    @Override
    public boolean isFileChosen(File file) {
        for (TransferRecord record : mChosenFiles) {
            if (record.getFile().equals(file)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void setFileAllChosen() {
        mChosenFiles.addAll(mDataSet);
        notifyDataSetChanged();

        Log.i(TAG, "All file chosen");
    }

    @Override
    public void setFileAllDismissed() {
        mOnChoosing = false;
        mChosenFiles.clear();
        notifyDataSetChanged();

        Log.i(TAG, "All file dismissed");
    }


    /**
     * 在非UI线程中删除
     */
    public void deleteChosenFiles() {
        List<TransferRecord> toDelete = new ArrayList<>();

        boolean allSucceeded = true;
        for (TransferRecord record : mChosenFiles) {
            if (record.getFile().delete()) {
                mDataSet.remove(record);
                toDelete.add(record);

                Log.i(TAG, "Delete file and record successfully: " + record.path);
            } else {
                allSucceeded = false;

                Log.i(TAG, "Failed to delete file and record: " + record.path);
            }
        }

        mChosenFiles.removeAll(toDelete);
        final int stringId = allSucceeded
                ? R.string.delete_succeeded
                : R.string.delete_partly_succeeded;

        mDataSet.refresh();

        mOnChoosing = mChosenFiles.size() != 0;

        context.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                notifyDataSetChanged();
                Toast.makeText(context, stringId, Toast.LENGTH_SHORT).show();
            }
        });
    }


    private void itemClicked(TransferRecord record) {
        if (!mChosenFiles.contains(record)) {
            mChosenFiles.add(record);
            mOnChoosingListener.onFileChosen(record.path);

        } else {
            mChosenFiles.remove(record);

            mOnChoosingListener.onFileDismissed(record.path);
        }

        mOnChoosing = mChosenFiles.size() != 0;
        notifyDataSetChanged();

    }

    public HistoryAdapter(final Activity context, final boolean showReceived) {
        this.context = context;
        this.mOnChoosingListener = (OnChoosingListener) context;


        Picasso.Builder builder = new Picasso.Builder(context);
        builder.addRequestHandler(new VideoIconRequestHandler());
        mVideoPicasso = builder.build();
        mImgPicasso = Picasso.with(context);

        int type = showReceived ? TransferRecord.TYPE_RECEIVED : TransferRecord.TYPE_SENT;
        mDataSet = TransferRecord.query(type);


        // TODO 文件可能已经被删除：将接收到的文件发送一次，出现在了发送纪录里面，然后接收纪录里面并没有删除之。或者文件被其它应用删除
        // TODO 在历史纪录里面发送，要更新发送纪录，或者直接退出历史界面算了
    }

    @Override
    public int getItemCount() {
        return mDataSet.size();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(context);
        View itemView = inflater.inflate(R.layout.list_history_item, parent, false);
        return new HistoryViewHolder(itemView);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder hd, int position) {
        final TransferRecord record = mDataSet.get(position);
        final File file = record.getFile();

        final HistoryViewHolder holder = (HistoryViewHolder) hd;

        if (StorageUtil.isVideoFile(file.getPath())) {
            mVideoPicasso.load(VideoIconRequestHandler.PICASSO_SCHEME_VIDEO + ":" + file.getAbsolutePath())
                    .resize(VideoIconRequestHandler.THUMB_SIZE, VideoIconRequestHandler.THUMB_SIZE)
                    .placeholder(R.drawable.ic_type_video)
                    .into(holder.iconImage);

        } else if (StorageUtil.isImgFile(file.getPath())) {
            mImgPicasso.load(file)
                    .resize(VideoIconRequestHandler.THUMB_SIZE, VideoIconRequestHandler.THUMB_SIZE)
                    .placeholder(R.drawable.ic_type_img)
                    .into(holder.iconImage);
        } else {
            holder.iconImage.setImageDrawable(StorageUtil.getFileIcon(context, file));
        }

        holder.nameText.setText(StorageUtil.getFileNameToDisplay(context, file));

        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ENGLISH);
        holder.infoText.setText(String.format("%s %s",
                format.format(new Date(record.timestamp)),
                StorageUtil.getReadableSize(record.size)));


        if (mChosenFiles.contains(record)) {
            holder.setItemStyleChosen(file);
        } else if (mOnChoosing) {
            holder.setItemStyleChoosing(file);
        } else {
            holder.setItemStyleNormal(file);
        }

        holder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isOnChoosing()) {
                    itemClicked(record);
                } else {
                    StorageUtil.openFile(context, file);
                }
            }
        });

        holder.itemView.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                itemClicked(record);
                return true;
            }
        });

        holder.optionToggleImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new AlertDialog.Builder(context)
                        .setMessage(context.getString(R.string.file_confirm_delete))
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                                if (file.delete()) {

                                    mDataSet.remove(record);
                                    mDataSet.refresh();


                                    if (mChosenFiles.contains(record)) {
                                        mChosenFiles.remove(record);

                                        mOnChoosing = mChosenFiles.size() != 0;
                                        mOnChoosingListener.onFileDismissed(record.path);
                                    }

                                    notifyDataSetChanged();


                                    Log.i(TAG, "Delete file and record successfully: " + file.getAbsolutePath());
                                    Toast.makeText(context, R.string.delete_succeeded, Toast.LENGTH_SHORT).show();
                                } else {

                                    Log.i(TAG, "Failed to delete file and record: " + file.getAbsolutePath());

                                    Toast.makeText(context, R.string.delete_failed, Toast.LENGTH_SHORT).show();
                                }
                            }
                        })
                        .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Toast.makeText(context, R.string.delete_dismissed, Toast.LENGTH_SHORT).show();
                            }
                        }).show();
            }
        });

    }


    class HistoryViewHolder extends RecyclerView.ViewHolder {

        @Bind(R.id.imageView_icon)
        ImageView iconImage;

        @Bind(R.id.textView_name)
        TextView nameText;

        @Bind(R.id.textView_info)
        TextView infoText;

        @Bind(R.id.imageButton_option)
        ImageButton optionToggleImage;


        public HistoryViewHolder(View itemView) {
            super(itemView);

            ButterKnife.bind(this, itemView);
        }


        /**
         * 正常显示样式
         *
         * @param file 对应的文件
         */
        public void setItemStyleNormal(File file) {
            itemView.setBackgroundColor(context.getResources().getColor(android.R.color.background_light));
            iconImage.setBackgroundDrawable(null);

            if (StorageUtil.isVideoFile(file.getPath())) {
                mVideoPicasso.load(VideoIconRequestHandler.PICASSO_SCHEME_VIDEO + ":" + file.getAbsolutePath())
                        .resize(VideoIconRequestHandler.THUMB_SIZE, VideoIconRequestHandler.THUMB_SIZE)
                        .placeholder(R.drawable.ic_type_video)
                        .tag(PICASSO_TAG)
                        .into(iconImage);

            } else if (StorageUtil.isImgFile(file.getPath())) {
                mImgPicasso.load(file)
                        .resize(VideoIconRequestHandler.THUMB_SIZE, VideoIconRequestHandler.THUMB_SIZE)
                        .placeholder(R.drawable.ic_type_img)
                        .tag(PICASSO_TAG)
                        .into(iconImage);
            } else {
                iconImage.setImageDrawable(StorageUtil.getFileIcon(context, file));
            }
        }

        /**
         * 待选样式
         *
         * @param file 对应的文件
         */
        public void setItemStyleChoosing(File file) {
            setItemStyleNormal(file);
            iconImage.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.shape_file_icon_bkg));
        }


        /**
         * 已选样式
         *
         * @param file 对应的文件
         */
        public void setItemStyleChosen(File file) {
            itemView.setBackgroundColor(context.getResources().getColor(R.color.file_item_chosen));
            iconImage.setImageDrawable(context.getResources().getDrawable(R.drawable.ic_file_chosen));
            iconImage.setBackgroundDrawable(context.getResources().getDrawable(R.drawable.shape_file_icon_bkg_chosen));
        }
    }

}
