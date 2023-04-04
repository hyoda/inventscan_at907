package kr.co.devmine.hoban.app.rfid.view;

import android.content.Intent;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import kr.co.devmine.hoban.app.rfid.GlobalInfo;
import kr.co.devmine.hoban.app.rfid.R;
import kr.co.devmine.hoban.app.rfid.adapter.TagListAdapter;
import kr.co.devmine.hoban.app.rfid.view.base.AccessActivity;
import kr.co.devmine.hoban.app.rfid.view.base.ActionActivity;
import com.atid.lib.dev.ATRfidReader;
import com.atid.lib.dev.rfid.ATRfid900MAReader;
import com.atid.lib.dev.rfid.ATRfidATX00S1Reader;
import com.atid.lib.dev.rfid.exception.ATRfidReaderException;
import com.atid.lib.dev.rfid.type.ActionState;
import com.atid.lib.dev.rfid.type.ResultCode;
import com.atid.lib.dev.rfid.type.TagType;
import com.atid.lib.diagnostics.ATLog;
import com.atid.lib.system.device.type.RfidModuleType;
import com.atid.lib.util.SysUtil;

import java.util.Locale;

public class InventoryActivity extends ActionActivity implements OnCheckedChangeListener, OnItemSelectedListener {

    private static final String TAG = InventoryActivity.class.getSimpleName();
    private static final boolean DEBUG_ENABLED = false;

    private static final int READ_MEMORY_ACTIVITY = 1;
    private static final int WRITE_MEMORY_ACTIVITY = 2;
    private static final int LOCK_MEMORY_ACTIVITY = 3;

    // Update Tag
    private static final int UPDATE_TIME = 500;

    // ------------------------------------------------------------------------
    // Member Variable
    // ------------------------------------------------------------------------

    private ListView lstTags;
    private CheckBox chkDisplayPc;
    private CheckBox chkContinuousMode;
    private CheckBox chkReportRssi;
    private CheckBox chkCwOn;

    private TextView txtCount;
    private Button btnAction;


    private TagListAdapter adpTags;

    private MenuItem mnuReadMemory;
    private MenuItem mnuWriteMemory;
    private MenuItem mnuLockMemory;

    private boolean mIsReportRssi;

    // Update Tag
    private Thread mThread;
    private boolean mIsAliveThread;

    // Total Tag Count
    private TextView txtTotalCount;
    private long m_totalCount;
    private ATRfid900MAReader mMAReader;
    // TPS Tag Count
    private TextView txtTagTpsCount;
    private int m_timeFlag;
    private long m_timeSec;
    private long m_tagTpsCount;



    // ------------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------------

    public InventoryActivity() {
        super();

        mView = R.layout.activity_inventory;

        mIsReportRssi = false;

        // Update Tag
        mThread = null;
        mIsAliveThread = false;

        // Total Tag Count
        m_totalCount =0;

        // TPS Tag Count
        m_timeFlag=0;
        m_timeSec=1;
        m_tagTpsCount=0;
    }

    // ------------------------------------------------------------------------
    // Activity Event Handler
    // ------------------------------------------------------------------------

    @Override
    protected void onStart() {

        ATLog.i(TAG, "INFO. onStart()");
        super.onStart();
    }

    @Override
    protected void onStop() {

        if (chkContinuousMode.isChecked()) {
            if (mIsAliveThread) {
                stopUpdateTagCount();
            }

            if(adpTags.isUpdate())
                adpTags.stopUpdate();
        }

        ATLog.i(TAG, "INFO. onStop()");
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();




        ATLog.d(TAG, "INFO onResume()");
    }




    @Override
    protected void onPause() {

        ATLog.i(TAG, "INFO. onPause()");
        super.onPause();
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        switch (buttonView.getId()) {
            case R.id.continue_mode:
                GlobalInfo.setContinuousMode(chkContinuousMode.isChecked());
                break;
            case R.id.display_pc:
                GlobalInfo.setDisplayPc(chkDisplayPc.isChecked());
                adpTags.setDisplayPc(GlobalInfo.isDisplayPc());
                break;
            case R.id.report_rssi:
                adpTags.setVisibleRssi(chkReportRssi.isChecked());
                break;
            case R.id.cw_on:
                ((ATRfidATX00S1Reader)mReader).carrierWaveOn(isChecked);
                break;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        if (v.getId() == R.id.tag_list && mReader.getAction() == ActionState.Stop) {
            getMenuInflater().inflate(R.menu.context_menu, menu);

            mnuReadMemory = menu.findItem(R.id.read_memory);
            mnuWriteMemory = menu.findItem(R.id.write_memory);
            mnuLockMemory = menu.findItem(R.id.lock_memory);

            switch(getTagType()) {
                case Tag6C:
                    mnuReadMemory.setVisible(true);
                    mnuWriteMemory.setVisible(true);
                    mnuLockMemory.setVisible(true);
                    enableWidgets(false);
                    break;
                case Tag6B:
                    mnuReadMemory.setVisible(true);
                    mnuWriteMemory.setVisible(true);
                    mnuLockMemory.setVisible(false);
                    enableWidgets(false);
                    break;
                default:
                    mnuReadMemory.setVisible(false);
                    mnuWriteMemory.setVisible(false);
                    mnuLockMemory.setVisible(false);
                    enableWidgets(true);
                    break;
            }
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Intent intent;
        AdapterView.AdapterContextMenuInfo menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int position = menuInfo.position;

        if (position < 0)
            return false;

        enableWidgets(false);
        mnuReadMemory.setEnabled(false);
        mnuWriteMemory.setEnabled(false);
        mnuLockMemory.setEnabled(false);

        String tag = adpTags.getItem(position);

        switch (item.getItemId()) {
            case R.id.read_memory:
                intent = new Intent(this, ReadMemoryActivity.class);
                intent.putExtra(AccessActivity.KEY_EPC, tag);
                intent.putExtra(AccessActivity.KEY_TAG_TYPE, getTagType().getCode());
                startActivityForResult(intent, READ_MEMORY_ACTIVITY);
                break;
            case R.id.write_memory:
                intent = new Intent(this, WriteMemoryActivity.class);
                intent.putExtra(AccessActivity.KEY_EPC, tag);
                intent.putExtra(AccessActivity.KEY_TAG_TYPE, getTagType().getCode());
                startActivityForResult(intent, WRITE_MEMORY_ACTIVITY);
                break;
            case R.id.lock_memory:
                intent = new Intent(this, LockMemoryActivity.class);
                intent.putExtra(AccessActivity.KEY_EPC, tag);
                intent.putExtra(AccessActivity.KEY_TAG_TYPE, getTagType().getCode());
                startActivityForResult(intent, LOCK_MEMORY_ACTIVITY);
                break;
        }
        return true;
    }

    @Override
    public void onContextMenuClosed(Menu menu) {
        enableWidgets(true);
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
    }

    // ------------------------------------------------------------------------
    // Reader Event Handler
    // ------------------------------------------------------------------------

    @Override
    public void onReaderActionChanged(ATRfidReader reader, ActionState action) {

        ATLog.e(TAG, "Action : " + action.toString());

        if (action == ActionState.Stop) {
            stopUpdateTagCount();

            adpTags.stopUpdate();
            txtCount.setText(String.format(Locale.US, "%d", adpTags.getCount()));
//            txtTotalCount.setText(String.format(Locale.US, "%d", m_totalCount));
//            txtTagTpsCount.setText(String.format(Locale.US, "%d",m_tagTpsCount));
        } else if (action == ActionState.CarrierWaveOn || action == ActionState.CarrierWaveOff){

        } else {
            adpTags.startUpdate();
        }

        enableWidgets(true);

        ATLog.i(TAG, "EVENT. onReaderActionchanged(%s)", action);
    }

    @Override
    public void onReaderReadTag(ATRfidReader reader, String tag, float rssi, float phase) {

        synchronized (adpTags) {
            adpTags.addItem(tag, rssi, phase);
        }
        m_totalCount++;				// Total Tag Count

        playSuccess();

        //ATLog.i(TAG, "EVENT. onReaderReadTag([%s], %.2f, %.2f)", tag, rssi, phase);
    }




    // ------------------------------------------------------------------------
    // Reader Control Methods
    // ------------------------------------------------------------------------

    // Start Action
    protected void startAction() {

        ResultCode res = ResultCode.NoError;
        TagType tagType = this.getTagType();

        GlobalInfo.setDisplayPc(chkDisplayPc.isChecked());
        adpTags.setDisplayPc(GlobalInfo.isDisplayPc());

        enableWidgets(false);

        if(mReader.getModuleType() == RfidModuleType.I900MA) {
            mMAReader = (ATRfid900MAReader)mReader;
            if (chkContinuousMode.isChecked()) {

                // Multi Reading
                startUpdateTagCount();
                //SysUtil.sleep(1000);

                //if ((res = mReader.inventory6cTag()) != ResultCode.NoError) {
                if(tagType == TagType.Tag6B) {
                    if ((res = mMAReader.inventory6bTag()) != ResultCode.NoError) {
                        ATLog.e(TAG, "ERROR. startAction() - Failed to start inventory 6B tag [%s]",
                                res);
                        stopUpdateTagCount();
                        enableWidgets(true);
                        if(res == ResultCode.NotSupported)
                            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else if(tagType == TagType.Tag6C) {
                    if ((res = mMAReader.inventory6cTag()) != ResultCode.NoError) {
                        ATLog.e(TAG, "ERROR. startAction() - Failed to start inventory 6C tag [%s]",
                                res);
                        stopUpdateTagCount();
                        enableWidgets(true);
                        if(res == ResultCode.NotSupported)
                            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else if(tagType == TagType.TagRail) {
                    if ((res = mMAReader.inventoryRailTag()) != ResultCode.NoError) {
                        ATLog.e(TAG, "ERROR. startAction() - Failed to start inventory Rail tag [%s]",
                                res);
                        stopUpdateTagCount();
                        enableWidgets(true);
                        if(res == ResultCode.NotSupported)
                            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else if(tagType == TagType.TagAny) {
                    if ((res = mMAReader.inventoryAnyTag()) != ResultCode.NoError) {
                        ATLog.e(TAG, "ERROR. startAction() - Failed to start inventory Any tag [%s]",
                                res);
                        stopUpdateTagCount();
                        enableWidgets(true);
                        if(res == ResultCode.NotSupported)
                            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            } else {
                // Single Reading
                if(tagType == TagType.Tag6B) {
                    if ((res = mMAReader.readEpc6bTag()) != ResultCode.NoError) {
                        ATLog.e(TAG,
                                "ERROR. startAction() - Failed to start read 6B tag [%s]", res);
                        enableWidgets(true);
                        if(res == ResultCode.NotSupported)
                            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else if(tagType == TagType.Tag6C) {
                    if ((res = mMAReader.readEpc6cTag()) != ResultCode.NoError) {
                        ATLog.e(TAG,
                                "ERROR. startAction() - Failed to start read 6C tag [%s]", res);
                        enableWidgets(true);
                        if(res == ResultCode.NotSupported)
                            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else if(tagType == TagType.TagRail) {
                    if ((res = mMAReader.readEpcRailTag()) != ResultCode.NoError) {
                        ATLog.e(TAG,
                                "ERROR. startAction() - Failed to start read Rail tag [%s]", res);
                        enableWidgets(true);
                        if(res == ResultCode.NotSupported)
                            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                } else if(tagType == TagType.TagAny) {
                    if ((res = mMAReader.readEpcAnyTag()) != ResultCode.NoError) {
                        ATLog.e(TAG,
                                "ERROR. startAction() - Failed to start read Any tag [%s]", res);
                        enableWidgets(true);
                        if(res == ResultCode.NotSupported)
                            Toast.makeText(this, R.string.not_supported, Toast.LENGTH_SHORT).show();
                        return;
                    }
                }
            }
        } else {
            if (chkContinuousMode.isChecked()) {

                if(mReader.getAction() != ActionState.Stop) {
                    ATLog.e(TAG, "ActionState is not idle.");
                    return;
                }

                // Multi Reading
                startUpdateTagCount();

                //((ATRfidATX00S1Reader)mReader).carrierWaveOn(true);


                if ((res = mReader.inventory6cTag()) != ResultCode.NoError) {
                    ATLog.e(TAG, "ERROR. startAction() - Failed to start inventory 6C tag [%s]",
                            res);
                    stopUpdateTagCount();
                    enableWidgets(true);
                    return;
                }

            } else {
                // Single Reading
                if ((res = mReader.readEpc6cTag()) != ResultCode.NoError) {
                    ATLog.e(TAG,
                            "ERROR. startAction() - Failed to start read 6C tag [%s]", res);

                    enableWidgets(true);
                    return;
                }
            }
        }

        ATLog.i(TAG, "INFO. startAction()");
    }

    // ------------------------------------------------------------------------
    // Override Widgets Control Methods
    // ------------------------------------------------------------------------

    @Override
    // Clear Widgets
    protected void clear() {
        adpTags.clear();
        txtCount.setText(String.format(Locale.US, "%d", adpTags.getCount()));

        // Total Tag Count
        m_totalCount=0;
//        txtTotalCount.setText(String.format(Locale.US, "%d",m_totalCount));

        // Tag TPS Count
        m_timeFlag=0;
        m_timeSec=1;
        m_tagTpsCount=0;
//        txtTagTpsCount.setText(String.format(Locale.US, "%d",m_tagTpsCount));
        ATLog.i(TAG, "INFO. clear()");
    }

    // Initialize Activity Widgets
    @Override
    protected void initWidgets() {
        super.initWidgets();

        // Initialize Tag List View
        lstTags = (ListView) findViewById(R.id.tag_list);
        adpTags = new TagListAdapter(this);
        lstTags.setAdapter(adpTags);
        lstTags.setOnItemSelectedListener(this);
        registerForContextMenu(lstTags);

        // Display PC Check Box
        chkDisplayPc = (CheckBox) findViewById(R.id.display_pc);
        chkDisplayPc.setOnCheckedChangeListener(this);

        // Continuous Mode Check Box
        chkContinuousMode = (CheckBox) findViewById(R.id.continue_mode);
        chkContinuousMode.setOnCheckedChangeListener(this);

        // Display RSSI Check Box
        chkReportRssi = (CheckBox) findViewById(R.id.report_rssi);
        chkReportRssi.setOnCheckedChangeListener(this);

        chkCwOn = (CheckBox)findViewById(R.id.cw_on);
        chkCwOn.setOnCheckedChangeListener(this);

        // Tag Count
        txtCount = (TextView) findViewById(R.id.tag_count);

//        // Total Tag Count
//        txtTotalCount = (TextView)findViewById(R.id.tag_total_count);
//        txtTotalCount.setText(String.format(Locale.US, "%d", m_totalCount));
//
//        // Tag TPS Count
//        txtTagTpsCount = (TextView)findViewById(R.id.tag_tps_count);
//        txtTagTpsCount.setText(String.format(Locale.US, "%d", m_tagTpsCount));

        // Action Button
        btnAction = (Button) findViewById(R.id.action);
        btnAction.setOnClickListener(this);



//        btnBarcode = (Button) findViewById(R.id.barcode);
//        btnBarcode.setOnClickListener(this);


        ATLog.i(TAG, "INFO. initWidgets()");
    }

    // Eanble Activity Widgets
    @Override
    protected void enableWidgets(boolean enabled) {
        super.enableWidgets(enabled);

        if (mReader.getAction() == ActionState.Stop) {
            chkDisplayPc.setEnabled(enabled);
            chkContinuousMode.setEnabled(enabled);
            chkReportRssi.setEnabled(enabled);
            btnAction.setText(R.string.action_inventory);
            btnAction.setEnabled(enabled);

        } else if(mReader.getAction() == ActionState.CarrierWaveOn) {
            chkDisplayPc.setEnabled(false);
            chkContinuousMode.setEnabled(false);
            chkReportRssi.setEnabled(false);
            btnAction.setText(R.string.action_stop);
            btnAction.setEnabled(false);

        } else {
            chkDisplayPc.setEnabled(false);
            chkContinuousMode.setEnabled(false);
            chkReportRssi.setEnabled(false);
            btnAction.setText(R.string.action_stop);
            btnAction.setEnabled(enabled);

        }
        //btnAction.setEnabled(enabled);
    }

    // Initialize Reader
    @Override
    protected void initReader() {
        super.initReader();

        // Get Report RSSI
        try {
            mIsReportRssi = mReader.getReportRssi();
        } catch (ATRfidReaderException e) {
            ATLog.e(TAG, e, "ERROR. initReader() - Failed to get report RSSI");
        }
        ATLog.i(TAG, "INFO. initReader() - [Report RSSI : %s]", mIsReportRssi);

        ATLog.i(TAG, "INFO initReader()");
    }

    // Activated Reader
    @Override
    protected void activateReader() {
        super.activateReader();

        chkDisplayPc.setChecked(GlobalInfo.isDisplayPc());
        chkContinuousMode.setChecked(GlobalInfo.isContinuousMode());
        chkReportRssi.setChecked(mIsReportRssi);
        adpTags.setDisplayPc(GlobalInfo.isContinuousMode());
        adpTags.setVisibleRssi(chkReportRssi.isChecked());

        enableWidgets(true);

        ATLog.i(TAG, "INFO. activateReader()");
    }

    // Update Tag
    private void startUpdateTagCount() {
        mThread = new Thread(mTimerThread);
        mThread.start();

        while(!mIsAliveThread) {
            SysUtil.sleep(5);
        }

        ATLog.i(TAG, "INFO. startUpdateTagCount()");
    }

    private void stopUpdateTagCount() {
        if (mThread == null)
            return;

        mIsAliveThread = false;
        try {
            mThread.join();
        } catch (InterruptedException e) {
            ATLog.e(TAG, "ERROR. stopUpdateTagCount() - Failed to join update list thread", e);
        }
        mThread = null;

        adpTags.notifyDataSetChanged();
        ATLog.i(TAG, "INFO. stopUpdateTagCount()");
    }

    private Runnable mTimerThread = new Runnable() {

        @Override
        public void run() {
            mIsAliveThread = true;

            while (mIsAliveThread) {
                runOnUiThread(mUpdateList);
                SysUtil.sleep(UPDATE_TIME);
            }
        }

    };

    private Runnable mUpdateList = new Runnable() {

        @Override
        public void run() {
//			synchronized (adpTags) {
            txtCount.setText(String.format(Locale.US, "%d", adpTags.getCount()));
//            txtTotalCount.setText(String.format(Locale.US, "%d", m_totalCount));

            m_timeFlag ^= 1;
            if(0 == m_timeFlag) {
                m_tagTpsCount = m_totalCount/m_timeSec ;
//                txtTagTpsCount.setText(String.format(Locale.US, "%d", m_tagTpsCount));
                if(DEBUG_ENABLED)
                    ATLog.d(TAG, "DEBUG. Tag [%d] , Total Tag [%d] , Tag per sec [%d]",
                            adpTags.getCount(), m_totalCount, m_tagTpsCount);
                m_timeSec++;
            }
//			}
        }
    };

}
