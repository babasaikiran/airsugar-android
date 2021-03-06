package com.imaginea.android.sugarcrm;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.ListFragment;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Filterable;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.imaginea.android.sugarcrm.CustomActionbar.AbstractAction;
import com.imaginea.android.sugarcrm.CustomActionbar.Action;
import com.imaginea.android.sugarcrm.CustomActionbar.IntentAction;
import com.imaginea.android.sugarcrm.provider.DatabaseHelper;
import com.imaginea.android.sugarcrm.provider.SugarCRMContent.Contacts;
import com.imaginea.android.sugarcrm.provider.SugarCRMContent.Recent;
import com.imaginea.android.sugarcrm.provider.SugarCRMProvider;
import com.imaginea.android.sugarcrm.ui.BaseMultiPaneActivity;
import com.imaginea.android.sugarcrm.util.ModuleField;
import com.imaginea.android.sugarcrm.util.Util;
import com.imaginea.android.sugarcrm.util.ViewUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * ModuleListFragment, lists the view projections for all the modules.
 * 
 */
public class ModuleListFragment extends ListFragment {

    private ListView mListView;

    private View mEmpty;

    private View mListFooterView;

    private TextView mListFooterText;

    private View mListFooterProgress;

    private boolean mBusy = false;

    private String mModuleName;

    private Uri mModuleUri;

    // private boolean mStopLoading = false;

    private Uri mIntentUri;

    private int mCurrentSelection;
    
    private ModuleSyncTask mSyncTask;

    // private static int mMaxResults = 20;

    private DatabaseHelper mDbHelper;

    private GenericCursorAdapter mAdapter;

    private static final int DIALOG_SORT_CHOICE = 1;

    private String[] mModuleFields;

    private String[] mModuleFieldsChoice;
    
    private Map<String, String> fieldMap = new HashMap<String, String>();

    private int mSortColumnIndex;

    private int MODE = Util.LIST_MODE;

    private String mSelections = ModuleFields.DELETED + "=?";

    private String[] mSelectionArgs = new String[] { Util.EXCLUDE_DELETED_ITEMS };

    private SugarCrmApp app;

    private boolean bRelationItem = true;

    public final static String LOG_TAG = ModuleListFragment.class.getSimpleName();

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        return inflater.inflate(R.layout.common_list, container, false);

    }

    /** {@inheritDoc} */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mDbHelper = new DatabaseHelper(getActivity().getBaseContext());
        app = (SugarCrmApp) getActivity().getApplication();
        Intent intent = getActivity().getIntent();

        // final Intent intent = BaseActivity.fragmentArgumentsToIntent(getArguments());
        Bundle extras = intent.getExtras();
        mModuleName = Util.CONTACTS;
        if (extras != null) {
            mModuleName = extras.getString(RestUtilConstants.MODULE_NAME);
        }

        mIntentUri = intent.getData();
        // If the list is a list of related items, hide the filterImage and
        // allItems image
        // if (mIntentUri != null && mIntentUri.getPathSegments().size() >= 3) {
        // getActivity().findViewById(R.id.filterImage).setVisibility(View.GONE);
        // getActivity().findViewById(R.id.allItems).setVisibility(View.GONE);

        // }

        // TextView tv = (TextView) getActivity().findViewById(R.id.headerText);
        // tv.setText(mModuleName);
        final CustomActionbar actionBar = (CustomActionbar) getActivity().findViewById(R.id.custom_actionbar);

        final Action homeAction = new IntentAction(ModuleListFragment.this.getActivity(), new Intent(ModuleListFragment.this.getActivity(), DashboardActivity.class), R.drawable.home);
        actionBar.setHomeAction(homeAction);
        actionBar.setTitle(mModuleName);

        if (mIntentUri == null || mIntentUri.getPathSegments().size() < 3) {
            actionBar.addActionItem(new IntentAction(ModuleListFragment.this.getActivity(), AddAction(), R.drawable.add));
            actionBar.addActionItem(new SearchAction());
            actionBar.addActionItem(new SortAction());
            actionBar.addActionItem(new SyncAction());
            actionBar.addActionItem(new ShowAllAction());
            actionBar.addActionItem(new ShowAssignedAction());

            bRelationItem = false;
        }

        mListView = getListView();

        // mListView.setOnScrollListener(this);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> arg0, View view, int position, long id) {
                addToRecent(position);
                openDetailScreen(position);
            }
        });

        // button code in the layout - 1.6 SDK feature to specify onClick
        mListView.setItemsCanFocus(true);
        mListView.setFocusable(true);
        mEmpty = getActivity().findViewById(R.id.empty);
        mListView.setEmptyView(mEmpty);
        registerForContextMenu(getListView());

        if (Log.isLoggable(LOG_TAG, Log.DEBUG)) {
            Log.d(LOG_TAG, "ModuleName:-->" + mModuleName);
        }

        mModuleUri = mDbHelper.getModuleUri(mModuleName);
        if (mIntentUri == null) {
            intent.setData(mModuleUri);
            mIntentUri = mModuleUri;
        }
        /*
         * Perform a managed query. The Activity will handle closing and requerying the cursor when
         * needed. TODO - optimize this, if we sync up a dataset, then no need to run detail
         * projection here, just do a list projection
         */
        Cursor cursor = getActivity().managedQuery(mIntentUri, mDbHelper.getModuleProjections(mModuleName), mSelections, mSelectionArgs, getSortOrder());

        String[] moduleSel = mDbHelper.getModuleListSelections(mModuleName);
        if (moduleSel.length >= 2)
            mAdapter = new GenericCursorAdapter(this.getActivity(), R.layout.contact_listitem, cursor, moduleSel, new int[] {
                    android.R.id.text1, android.R.id.text2 });
        else
            mAdapter = new GenericCursorAdapter(this.getActivity(), R.layout.contact_listitem, cursor, moduleSel, new int[] { android.R.id.text1 });
        setListAdapter(mAdapter);
        // make the list filterable using the keyboard
        mListView.setTextFilterEnabled(true);

        TextView tv1 = (TextView) (mEmpty.findViewById(R.id.mainText));

        if (mAdapter.getCount() == 0) {
            mListView.setVisibility(View.GONE);
            mEmpty.findViewById(R.id.progress).setVisibility(View.INVISIBLE);
            tv1.setVisibility(View.VISIBLE);
            if (mModuleName != null) {
                tv1.setText("No " + mModuleName + " found");
            }
        } else {
            mEmpty.findViewById(R.id.progress).setVisibility(View.VISIBLE);
            tv1.setVisibility(View.GONE);
        }

        mListFooterView = ((LayoutInflater) getActivity().getSystemService(Context.LAYOUT_INFLATER_SERVICE)).inflate(R.layout.list_item_footer, mListView, false);
        getListView().addFooterView(mListFooterView);
        mListFooterText = (TextView) getActivity().findViewById(R.id.status);

        mListFooterProgress = mListFooterView.findViewById(R.id.progress);

        // get the sort options
        // get the LIST projection
        mModuleFields = mDbHelper.getModuleListSelections(mModuleName);
        // get the module fields for the module
        Map<String, ModuleField> map = mDbHelper.getModuleFields(mModuleName);
        if (map == null) {
            Log.w(LOG_TAG, "Cannot prepare Options as Map is null for module:" + mModuleName);
            // TODO return false;
            return;
        }
        mModuleFieldsChoice = new String[mModuleFields.length];
        for (int i = 0; i < mModuleFields.length; i++) {
            // add the module field label to be displayed in the choice menu
            ModuleField modField = map.get(mModuleFields[i]);
            if (modField != null) {
                mModuleFieldsChoice[i] = modField.getLabel();
                // fieldMap: label vs name
                fieldMap.put(mModuleFieldsChoice[i], mModuleFields[i]);
            } else
                mModuleFieldsChoice[i] = "";
            if (mModuleFieldsChoice[i].indexOf(":") > 0) {
                mModuleFieldsChoice[i] = mModuleFieldsChoice[i].substring(0, mModuleFieldsChoice[i].length() - 1);
                fieldMap.put(mModuleFieldsChoice[i], mModuleFields[i]);
            }
        }
    }

    /**
     * GenericCursorAdapter
     */
    private final class GenericCursorAdapter extends SimpleCursorAdapter implements Filterable {

        private int realoffset = 0;

        private int limit = 20;

        private ContentResolver mContent;

        public GenericCursorAdapter(Context context, int layout, Cursor c, String[] from, int[] to) {
            super(context, layout, c, from, to);
            mContent = context.getContentResolver();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {

            View v = super.getView(position, convertView, parent);
            int count = getCursor().getCount();
            if (!mBusy && position != 0 && position == count - 1) {
                mBusy = true;
                realoffset += count;
                // Uri uri = getIntent().getData();
                // TODO - fix this, this is no longer used
                Uri newUri = Uri.withAppendedPath(Contacts.CONTENT_URI, realoffset + "/" + limit);
                Log.d(LOG_TAG, "Changing cursor:" + newUri.toString());
                final Cursor cursor = getActivity().managedQuery(newUri, Contacts.LIST_PROJECTION, null, null, Contacts.DEFAULT_SORT_ORDER);
                CRMContentObserver observer = new CRMContentObserver(new Handler() {

                    @Override
                    public void handleMessage(Message msg) {
                        super.handleMessage(msg);
                        Log.d(LOG_TAG, "Changing cursor: in handler");
                        // if (cursor.getCount() < mMaxResults)
                        // mStopLoading = true;
                        changeCursor(cursor);
                        mListFooterText.setVisibility(View.GONE);
                        mListFooterProgress.setVisibility(View.GONE);
                        mBusy = false;
                    }
                });
                cursor.registerContentObserver(observer);
            }
            if (mBusy) {
                mListFooterProgress.setVisibility(View.VISIBLE);
                mListFooterText.setVisibility(View.VISIBLE);
                mListFooterText.setText("Loading...");
                // Non-null tag means the view still needs to load it's data
                // text.setTag(this);
            }
            return v;
        }

        @Override
        public String convertToString(Cursor cursor) {
            return cursor.getString(2);
        }

        @Override
        public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
            if (getFilterQueryProvider() != null) {
                return getFilterQueryProvider().runQuery(constraint);
            }

            StringBuilder buffer = null;
            String[] args = null;
            if (constraint != null) {
                buffer = new StringBuilder();
                buffer.append("UPPER(");
                buffer.append(mDbHelper.getModuleListSelections(mModuleName)[0]);
                buffer.append(") GLOB ?");
                args = new String[] { constraint.toString().toUpperCase() + "*" };
            }

            return mContent.query(mDbHelper.getModuleUri(mModuleName), mDbHelper.getModuleListProjections(mModuleName), buffer == null ? null
                                            : buffer.toString(), args, mDbHelper.getModuleSortOrder(mModuleName));
        }
    }

    /**
     * opens the Detail Screen
     * 
     * @param position
     */
    void openDetailScreen(int position) {

        Cursor cursor = (Cursor) getListAdapter().getItem(position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            Log.w(LOG_TAG, "openDetailScreen, Cursor is null for " + position);
            return;
        }
        Intent detailIntent = new Intent(this.getActivity(), ModuleDetailActivity.class);
        detailIntent.putExtra(Util.ROW_ID, cursor.getString(0));
        detailIntent.putExtra(RestUtilConstants.BEAN_ID, cursor.getString(1));
        detailIntent.putExtra(RestUtilConstants.MODULE_NAME, mModuleName);
        detailIntent.putExtra("Relation", bRelationItem);
        if (ViewUtil.isTablet(getActivity())) {
            /*
             * We can display everything in-place with fragments. Have the list highlight this item
             * and show the data. Check what fragment is shown, replace if needed.
             */
            // ModuleDetailFragment details = (ModuleDetailFragment)
            // getFragmentManager().findFragmentByTag("module_detail");
            // Log.d("onClick list item", "details is null");
            ((BaseMultiPaneActivity) getActivity()).openActivityOrFragment(detailIntent);

        } else {
            startActivity(detailIntent);
        }
    }

    /**
     * opens the Edit Screen
     * 
     * @param position
     */
    private void openEditScreen(int position) {

        Cursor cursor = (Cursor) getListAdapter().getItem(position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }
        Intent editDetailsIntent = new Intent(this.getActivity(), EditModuleDetailActivity.class);
        editDetailsIntent.putExtra(Util.ROW_ID, cursor.getString(0));
        if (mIntentUri != null)
            editDetailsIntent.setData(Uri.withAppendedPath(mIntentUri, cursor.getString(0)));

        editDetailsIntent.putExtra(RestUtilConstants.BEAN_ID, cursor.getString(1));
        editDetailsIntent.putExtra(RestUtilConstants.MODULE_NAME, mModuleName);

        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
            Log.d(LOG_TAG, "beanId:" + cursor.getString(1));
        
        startActivity(editDetailsIntent);
        
        /*ModuleDetailFragment details = (ModuleDetailFragment) getFragmentManager().findFragmentByTag("module_detail");
        //if (details != null) {
            *
             * We can display everything in-place with fragments. Have the list highlight this item
             * and show the data. Make new fragment to show this selection.
             *
          //  getListView().setItemChecked(position, true);
         //   ((BaseMultiPaneActivity) getActivity()).openActivityOrFragment(editDetailsIntent);

        //} else {
            startActivity(editDetailsIntent);
       // }*/
    }

    /**
     * deletes an item
     */
    void deleteItem() {
        Cursor cursor = (Cursor) getListAdapter().getItem(mCurrentSelection);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        String beanId = cursor.getString(1);
        if (Log.isLoggable(LOG_TAG, Log.DEBUG))
            Log.d(LOG_TAG, "beanId:" + beanId);

        if (mDbHelper == null)
            mDbHelper = new DatabaseHelper(getActivity().getBaseContext());

        mModuleUri = mDbHelper.getModuleUri(mModuleName);
        Uri deleteUri = Uri.withAppendedPath(mModuleUri, cursor.getString(0));
        getActivity().getContentResolver().registerContentObserver(deleteUri, false, new DeleteContentObserver(new Handler()));
        ServiceHelper.startServiceForDelete(getActivity().getBaseContext(), deleteUri, mModuleName, beanId);
        ContentValues values = new ContentValues();
        values.put(ModuleFields.DELETED, Util.DELETED_ITEM);
        getActivity().getBaseContext().getContentResolver().update(deleteUri, values, null, null);
        
    }     

    private static class DeleteContentObserver extends ContentObserver {

        public DeleteContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public boolean deliverSelfNotifications() {
            return super.deliverSelfNotifications();
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
        }
    }

    /** {@inheritDoc} */
    @Override
    public void onPause() {
        super.onPause();
        Log.i(LOG_TAG, "onPause");
        if (mSyncTask != null && mSyncTask.getStatus() == AsyncTask.Status.RUNNING) {
            mSyncTask.cancel(true);
            }
    }

    /** {@inheritDoc} */
    // TODO
    // @Override
    public boolean onSearchRequested() {
        Bundle appData = new Bundle();
        String[] modules = { mModuleName };
        appData.putString(RestUtilConstants.MODULE_NAME, mModuleName);
        appData.putStringArray(RestUtilConstants.MODULES, modules);
        appData.putInt(RestUtilConstants.OFFSET, 0);
        appData.putInt(RestUtilConstants.MAX_RESULTS, 20);

        getActivity().startSearch(null, false, appData, false);
        return true;
    }

    /** {@inheritDoc} */
    // TODO
    // @Override
    public void onPrepareOptionsMenu(Menu menu) {
        MenuHelper.onPrepareOptionsMenu(this.getActivity(), menu, mModuleName);

        // get the sort options
        // get the LIST projection
        mModuleFields = mDbHelper.getModuleListSelections(mModuleName);
        // get the module fields for the module
        Map<String, ModuleField> map = mDbHelper.getModuleFields(mModuleName);
        if (map == null) {
            Log.w(LOG_TAG, "Cannot prepare Options as Map is null for module:" + mModuleName);
            // TODO return false;
            return;
        }
        mModuleFieldsChoice = new String[mModuleFields.length];
        for (int i = 0; i < mModuleFields.length; i++) {
            // add the module field label to be displayed in the choice menu
            ModuleField modField = map.get(mModuleFields[i]);
            if (modField != null)
                mModuleFieldsChoice[i] = modField.getLabel();
            else
                mModuleFieldsChoice[i] = "";
            if (mModuleFieldsChoice[i].indexOf(":") > 0) {
                mModuleFieldsChoice[i] = mModuleFieldsChoice[i].substring(0, mModuleFieldsChoice[i].length() - 1);
            }
        }

        if (!mModuleName.equalsIgnoreCase(Util.CONTACTS)) {
            menu.findItem(R.id.importContact).setVisible(false);
        }

        // TODO
        super.onPrepareOptionsMenu(menu);
        return;
    }

    /** {@inheritDoc} */
    // TODO
    // @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        // Inflate the currently selected menu XML resource.
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.list_activity_menu, menu);

        return true;
    }

    /** {@inheritDoc} */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.home:
            showHome();
            return true;
        case R.id.search:
            onSearchRequested();
            return true;
        case R.id.addItem:
            Intent myIntent = new Intent(this.getActivity(), EditModuleDetailActivity.class);
            myIntent.putExtra(RestUtilConstants.MODULE_NAME, mModuleName);
            Log.v(LOG_TAG, "intetnURI: " + mIntentUri);
            if (mIntentUri != null)
                myIntent.setData(mIntentUri);
            this.startActivity(myIntent);
            return true;
        case R.id.sort:
            // TODO
            getActivity().showDialog(DIALOG_SORT_CHOICE);
            return true;
        case R.id.importContact:
            myIntent = new Intent(this.getActivity(), EditModuleDetailActivity.class);
            myIntent.putExtra(RestUtilConstants.MODULE_NAME, mModuleName);
            myIntent.putExtra(Util.IMPORT_FLAG, Util.CONTACT_IMPORT_FLAG);
            if (mIntentUri != null)
                myIntent.setData(mIntentUri);
            // TODO
            this.startActivity(myIntent);
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    // TODO
    // @Override
    /*
     * protected void onPrepareDialog(int id, Dialog dialog, Bundle args) {
     * super.onPrepareDialog(id, dialog, args); }
     */

    /** {@inheritDoc} */
    // @Override
    // TODO
    /*
     * protected void onPrepareDialog(int id, Dialog dialog) { super.onPrepareDialog(id, dialog); }
     */

    /** {@inheritDoc} */
    // @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
        case DIALOG_SORT_CHOICE:
            Builder builder = new AlertDialog.Builder(this.getActivity());
            builder.setIcon(android.R.drawable.ic_dialog_alert);
            builder.setTitle(R.string.sortBy);

            mSortColumnIndex = 0;
            builder.setSingleChoiceItems(mModuleFieldsChoice, 0, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    mSortColumnIndex = whichButton;
                }
            });
            builder.setPositiveButton(R.string.ascending, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String sortOrder = mModuleFields[mSortColumnIndex] + " ASC";
                    sortList(sortOrder);
                }
            });
            builder.setNegativeButton(R.string.descending, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String sortOrder = mModuleFields[mSortColumnIndex] + " DESC";
                    sortList(sortOrder);
                }
            });
            return builder.create();

        case R.string.delete:

            return new AlertDialog.Builder(this.getActivity()).setTitle(id).setMessage(R.string.deleteAlert).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                    deleteItem();

                }
            }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                }
            }).create();
        }

        return null;
    }

    /** {@inheritDoc} */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
        menu.setHeaderTitle(R.string.options);
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(LOG_TAG, "Bad menuInfo", e);
            return;
        }

        if (mDbHelper == null)
            mDbHelper = new DatabaseHelper(getActivity().getBaseContext());

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }
        int index = cursor.getColumnIndex(ModuleFields.CREATED_BY_NAME);
        String ownerName = cursor.getString(index);

        menu.add(1, R.string.view, 2, R.string.view).setEnabled(mDbHelper.isAclEnabled(mModuleName, RestUtilConstants.VIEW, ownerName));
        menu.add(2, R.string.edit, 3, R.string.edit).setEnabled(mDbHelper.isAclEnabled(mModuleName, RestUtilConstants.EDIT, ownerName));
        menu.add(3, R.string.delete, 4, R.string.delete).setEnabled(mDbHelper.isAclEnabled(mModuleName, RestUtilConstants.DELETE, ownerName));

        // TODO disable options based on acl actions for the module

        // TODO
        if (mDbHelper.getModuleField(mModuleName, ModuleFields.PHONE_WORK) != null)
            menu.add(4, R.string.call, 4, R.string.call);
        if (mDbHelper.getModuleField(mModuleName, ModuleFields.EMAIL1) != null)
            menu.add(5, R.string.email, 4, R.string.email);

    }

    /** {@inheritDoc} */
    @Override
    public boolean onContextItemSelected(MenuItem item) {

        AdapterView.AdapterContextMenuInfo info;

        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(LOG_TAG, "bad menuInfo", e);
            return false;
        }
        int position = info.position;

        addToRecent(position);

        switch (item.getItemId()) {
        case R.string.view:
            openDetailScreen(position);
            return true;

        case R.string.edit:
            openEditScreen(position);
            return true;

        case R.string.delete:
            mCurrentSelection = position;
            // getActivity().showDialog(R.string.delete);
            DialogFragment newFragment = new MyYesNoAlertDialogFragment().newInstance(R.string.delete);
            newFragment.show(getFragmentManager(), "dialog");
            return true;

        case R.string.call:
            callNumber(position);
            return true;

        case R.string.email:
            sendMail(position);
            return true;

        }

        return super.onOptionsItemSelected(item);
    }

    private void sortList(String sortOrder) {
        String selection = null;
        if (MODE == Util.ASSIGNED_ITEMS_MODE) {
            // TODO: get the user name from Account Manager
            String userName = SugarCrmSettings.getUsername(this.getActivity());
            selection = ModuleFields.ASSIGNED_USER_NAME + "='" + userName + "'";
        }
        Cursor cursor = getActivity().managedQuery(mIntentUri, mDbHelper.getModuleProjections(mModuleName), selection, null, sortOrder);
        mAdapter.changeCursor(cursor);
        mAdapter.notifyDataSetChanged();
    }

    void addToRecent(int position) {
        ContentValues modifiedValues = new ContentValues();
        // push the selected record into recent table
        Cursor cursor = (Cursor) getListAdapter().getItem(position);

        // now insert into recent table
        modifiedValues.put(Recent.ACTUAL_ID, cursor.getInt(0) + "");
        modifiedValues.put(Recent.BEAN_ID, cursor.getString(1));
        modifiedValues.put(Recent.NAME_1, cursor.getString(2));
        modifiedValues.put(Recent.NAME_2, cursor.getString(3));
        modifiedValues.put(Recent.REF_MODULE_NAME, mModuleName);
        modifiedValues.put(Recent.DELETED, "0");
        Uri insertResultUri = getActivity().getApplicationContext().getContentResolver().insert(Recent.CONTENT_URI, modifiedValues);
        Log.i(LOG_TAG, "insertResultURi - " + insertResultUri);

    }

    /**
     * <p>
     * showAssignedItems
     * </p>
     * 
     * @param view
     *            a {@link android.view.View} object.
     */
    public void showAssignedItems(View view) {
        MODE = Util.ASSIGNED_ITEMS_MODE;
        // TODO: get the user name from Account Manager
        String userName = SugarCrmSettings.getUsername(this.getActivity());
        String selection = ModuleFields.ASSIGNED_USER_NAME + "='" + userName + "'";
        Cursor cursor = getActivity().managedQuery(mIntentUri, mDbHelper.getModuleProjections(mModuleName), selection, null, getSortOrder());

        mAdapter.changeCursor(cursor);
        mAdapter.notifyDataSetChanged();

        TextView mainTextView = (TextView) (mEmpty.findViewById(R.id.mainText));
        if (mAdapter.getCount() == 0) {
            mListView.setVisibility(View.GONE);
            mEmpty.findViewById(R.id.progress).setVisibility(View.INVISIBLE);
            mainTextView.setVisibility(View.VISIBLE);
            if (mModuleName != null) {
                mainTextView.setText("No " + mModuleName + " found");
            }
        } else {
            mEmpty.findViewById(R.id.progress).setVisibility(View.VISIBLE);
            mainTextView.setVisibility(View.GONE);
        }

        if (ViewUtil.isTablet(getActivity())) {
            Intent myIntent = new Intent(this.getActivity(), ModuleDetailActivity.class);
            myIntent.putExtra(Util.ROW_ID, "1");
            myIntent.putExtra(RestUtilConstants.MODULE_NAME, mModuleName);
            ((BaseMultiPaneActivity) getActivity()).openActivityOrFragment(myIntent);
        }
    }

    /**
     * <p>
     * showAllItems
     * </p>
     * 
     * @param view
     *            a {@link android.view.View} object.
     */
    public void showAllItems(View view) {
        
        MODE = Util.LIST_MODE;
        Cursor cursor = getActivity().managedQuery(mIntentUri, mDbHelper.getModuleProjections(mModuleName), null, null, getSortOrder());
        mAdapter.changeCursor(cursor);
        mAdapter.notifyDataSetChanged();

        TextView mainTextView = (TextView) (mEmpty.findViewById(R.id.mainText));
        if (mAdapter.getCount() == 0) {
            mListView.setVisibility(View.GONE);
            mEmpty.findViewById(R.id.progress).setVisibility(View.INVISIBLE);
            mainTextView.setVisibility(View.VISIBLE);
            if (mModuleName != null) {
                mainTextView.setText("No " + mModuleName + " found");
            }
        } else {
            mEmpty.findViewById(R.id.progress).setVisibility(View.VISIBLE);
            mainTextView.setVisibility(View.GONE);
        }
    }

    /**
     * <p>
     * show Home Screen
     * </p>
     * 
     * @param view
     *            a {@link android.view.View} object.
     */
    public void showHome(View view) {
        // showHome();
    }

    private void showHome() {
        Intent homeIntent = new Intent(getActivity(), DashboardActivity.class);
        startActivity(homeIntent);
    }

    private String getSortOrder() {
        String sortOrder = null;
        Map<String, String> sortOrderMap = app.getModuleSortOrder(mModuleName);
        for (Entry<String, String> entry : sortOrderMap.entrySet()) {
            sortOrder = entry.getKey() + " " + entry.getValue();
        }
        return sortOrder;
    }

    /**
     * <p>
     * callNumber
     * </p>
     * 
     * @param position
     *            a int.
     */
    public void callNumber(int position) {
        Cursor cursor = (Cursor) getListAdapter().getItem(position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }

        int index = cursor.getColumnIndex(ModuleFields.PHONE_WORK);
        String number = cursor.getString(index);
        Intent intent = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + number));
        startActivity(intent);
    }

    /**
     * <p>
     * sendMail
     * </p>
     * 
     * @param position
     *            a int.
     */
    public void sendMail(int position) {
        Cursor cursor = (Cursor) getListAdapter().getItem(position);
        if (cursor == null) {
            // For some reason the requested item isn't available, do nothing
            return;
        }
        // emailAddress
        int index = cursor.getColumnIndex(ModuleFields.EMAIL1);
        String emailAddress = cursor.getString(index);
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("mailto:" + emailAddress));
        startActivity(Intent.createChooser(intent, getActivity().getString(R.string.email)));
    }

    // Task to sync individual module
    class ModuleSyncTask extends AsyncTask<Object, Object, Object> {

        boolean hasExceptions = false;

        SharedPreferences prefs;

        ProgressDialog mProgressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            prefs = PreferenceManager.getDefaultSharedPreferences(ModuleListFragment.this.getActivity());
            mProgressDialog = ViewUtil.getProgressDialog(ModuleListFragment.this.getActivity(), "Syncing...", true);
            mProgressDialog.show();
        }

        @Override
        protected Object doInBackground(Object... args) {
            startModuleSync();
            return true;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
        }

        @Override
        protected void onPostExecute(Object sessionId) {
            super.onPostExecute(sessionId);
            if (isCancelled())
                return;

            mProgressDialog.cancel();
            mProgressDialog = null;
        }

        @SuppressWarnings("deprecation")
        private void startModuleSync() {
            Log.d(LOG_TAG, "startModuleSync");
            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_IGNORE_SETTINGS, true);
            extras.putInt(Util.SYNC_TYPE, Util.SYNC_MODULE_DATA);
            extras.putString(RestUtilConstants.MODULE_NAME, mModuleName);
            SugarCrmApp app = (SugarCrmApp) ModuleListFragment.this.getActivity().getApplication();
            final String usr = SugarCrmSettings.getUsername(ModuleListFragment.this.getActivity()).toString();
            ContentResolver.requestSync(app.getAccount(usr), SugarCRMProvider.AUTHORITY, extras);
        }
    }

    private Intent AddAction() {
        Intent myIntent = new Intent(this.getActivity(), EditModuleDetailActivity.class);
        myIntent.putExtra(RestUtilConstants.MODULE_NAME, mModuleName);
        return myIntent;
    }

    private class SearchAction extends AbstractAction {

        public SearchAction() {
            super(R.drawable.search);
        }

        @Override
        public void performAction(View view) {
            onSearchRequested();
        }
    }

    public class MyAlertDialogFragment extends DialogFragment {

        public MyAlertDialogFragment newInstance(int title) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("title", title);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int title = getArguments().getInt("title");
            mSortColumnIndex = 0;
            
            Map<String, String> sortOrderMap = app.getModuleSortOrder(mModuleName);
            if(sortOrderMap != null) {
                for (Entry<String, String> entry : sortOrderMap.entrySet()) {
                    for (mSortColumnIndex = 0; mSortColumnIndex < mModuleFieldsChoice.length;) {
                        if(fieldMap.get(mModuleFieldsChoice[mSortColumnIndex]).equals(entry.getKey()))
                            break;
                        mSortColumnIndex++;
                    }                    
                }        
            }
            if(mSortColumnIndex == mModuleFieldsChoice.length)
                mSortColumnIndex = 0;
            return new AlertDialog.Builder(getActivity()).setIcon(android.R.drawable.ic_dialog_alert).setTitle(title).setSingleChoiceItems(mModuleFieldsChoice, mSortColumnIndex, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    mSortColumnIndex = whichButton;
                }
            }).setPositiveButton(R.string.ascending, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    String sortOrder = mModuleFields[mSortColumnIndex] + " ASC";
                    app.setModuleSortOrder(mModuleName, fieldMap.get(mModuleFieldsChoice[mSortColumnIndex]), "ASC");
                    sortList(sortOrder);
                }
            })

            .setNegativeButton(R.string.descending, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                    // ((FragmentAlertDialog)getActivity()).doNegativeClick();
                    String sortOrder = mModuleFields[mSortColumnIndex] + " DESC";
                    app.setModuleSortOrder(mModuleName, fieldMap.get(mModuleFieldsChoice[mSortColumnIndex]), "DESC");
                    sortList(sortOrder);
                }
            }).create();
        }
    }

    private class SortAction extends AbstractAction {

        public SortAction() {
            super(R.drawable.more, "Sort");
        }

        @Override
        public void performAction(View view) {
            DialogFragment newFragment = new MyAlertDialogFragment().newInstance(R.string.sortBy);
            newFragment.show(getFragmentManager(), "dialog");
        }
    }

    private class SyncAction extends AbstractAction {

        public SyncAction() {
            super(R.drawable.more, "Sync");
        }

        @Override
        public void performAction(View view) {
        	if(!Util.isNetworkOn(ModuleListFragment.this.getActivity().getBaseContext())) {
        		Toast.makeText(ModuleListFragment.this.getActivity(), R.string.networkUnavailable, Toast.LENGTH_SHORT).show();
        	} else {
        	    SugarCrmApp app = (SugarCrmApp) ModuleListFragment.this.getActivity().getApplication();
                final String usr = SugarCrmSettings.getUsername(ModuleListFragment.this.getActivity()).toString();
        	    if(ContentResolver.isSyncActive(app.getAccount(usr), SugarCRMProvider.AUTHORITY))
                {
                    AlertDialog alertDialog = new AlertDialog.Builder(ModuleListFragment.this.getActivity()).create();
                    alertDialog.setTitle(R.string.info);
                    alertDialog.setMessage(getString(R.string.syncProgressMsg));
                    alertDialog.setIcon(R.drawable.applaunch);
                    alertDialog.setButton(getString(android.R.string.ok), new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {}});
                    alertDialog.show();
                    return;
                }        	    
        	    mSyncTask = new ModuleSyncTask();
        		mSyncTask.execute();
        	}
        }
    }

    private class ShowAllAction extends AbstractAction {

        public ShowAllAction() {
            super(R.drawable.more, "All");
        }

        @Override
        public void performAction(View view) {
            showAllItems(view);
        }
    }

    private class ShowAssignedAction extends AbstractAction {

        public ShowAssignedAction() {
            super(R.drawable.more, "Assigned");
        }

        @Override
        public void performAction(View view) {
            showAssignedItems(view);
        }
    }

    private class MyYesNoAlertDialogFragment extends DialogFragment {

        public MyYesNoAlertDialogFragment newInstance(int title) {
            MyYesNoAlertDialogFragment frag = new MyYesNoAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("title", title);
            frag.setArguments(args);
            return frag;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {

            return new AlertDialog.Builder(this.getActivity()).setTitle(R.string.delete).setMessage(R.string.deleteAlert).setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {

                    deleteItem();
                    if (ViewUtil.isTablet(getActivity())) {
                        ModuleDetailFragment fragment = (ModuleDetailFragment) getActivity().getSupportFragmentManager().findFragmentByTag("module_detail");
                        getActivity().getSupportFragmentManager().beginTransaction().remove(fragment).commit();
                        ModuleDetailFragment moduleDetailFragment = new ModuleDetailFragment();
                        getActivity().getSupportFragmentManager().beginTransaction().add(R.id.fragment_container_module_detail, moduleDetailFragment, "module_detail").commit();
                    }
                }
            }).setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int whichButton) {
                }
            }).create();
        }
    }
}