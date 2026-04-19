package se.accidis.fmfg.app.ui.documents;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.util.Log;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.ListFragment;

import java.util.List;

import se.accidis.fmfg.app.R;
import se.accidis.fmfg.app.model.DocumentLink;
import se.accidis.fmfg.app.services.DocumentsRepository;
import se.accidis.fmfg.app.ui.MainActivity;
import se.accidis.fmfg.app.utils.AndroidUtils;

/**
 * Fragment showing the list of saved documents.
 */
public final class DocumentsListFragment extends ListFragment implements MainActivity.HasNavigationItem {
    private static final String STATE_LIST_VIEW = "documentListViewState";
    private static final String TAG = DocumentsListFragment.class.getSimpleName();
    private final Handler mHandler = new Handler();
    private List<DocumentLink> mDocumentsList;
    private DocumentsListAdapter mListAdapter;
    private DocumentsRepository mRepository;
    private boolean mIsLoaded;
    private Parcelable mListState;

    @Override
    public int getItemId() {
        return R.id.nav_documents_list;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        registerForContextMenu(getListView());
        if (savedInstanceState != null) {
            mListState = savedInstanceState.getParcelable(STATE_LIST_VIEW);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        setEmptyText(getString(R.string.documents_list_empty));
        AndroidUtils.hideSoftKeyboard(getContext(), getView());

        mRepository = DocumentsRepository.getInstance(getContext());
        if (!mIsLoaded || !mRepository.isLoaded()) {
            mRepository.setOnLoadedListener(new DocumentsLoadedListener());
            mRepository.beginLoad();
        } else {
            initializeList();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (null != getView()) {
            outState.putParcelable(STATE_LIST_VIEW, getListView().onSaveInstanceState());
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        if (null == mListAdapter || position < 0 || position >= mListAdapter.getCount()) {
            return;
        }

        openDocument((DocumentLink) mListAdapter.getItem(position));
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        if (null == mListAdapter || info.position < 0 || info.position >= mListAdapter.getCount()) {
            return false;
        }

        DocumentLink docLink = (DocumentLink) mListAdapter.getItem(info.position);
        switch (item.getItemId()) {
            case R.id.document_link_menu_edit:
                editDocument(docLink);
                return true;
            case R.id.document_link_menu_delete:
                showDeleteDialog(docLink);
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getActivity().getMenuInflater();
        inflater.inflate(R.menu.documents_list, menu);
    }

    private void openDocument(DocumentLink docLink) {
        DocumentFragment fragment = DocumentFragment.createInstance(docLink);
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            saveInstanceState();
            ((MainActivity) activity).openFragment(fragment);
        } else {
            Log.e(TAG, "Activity holding fragment is not MainActivity!");
        }
    }

    private void initializeList() {
        mListAdapter = new DocumentsListAdapter(getContext(), mDocumentsList);
        setListAdapter(mListAdapter);

        if (null != mListState) {
            getListView().onRestoreInstanceState(mListState);
            mListState = null;
        }
    }

    private void saveInstanceState() {
        mListState = getListView().onSaveInstanceState();
    }

    private void editDocument(DocumentLink docLink) {
        if (null == mRepository) {
            mRepository = DocumentsRepository.getInstance(getContext());
        }
        if (mRepository.getCurrentDocument().hasUnsavedChanges()) {
            final EditUnsavedDialogFragment editDialog = new EditUnsavedDialogFragment();
            editDialog.setDialogListener(new EditUnsavedDialogListener(docLink));
            editDialog.show(getFragmentManager(), EditUnsavedDialogFragment.class.getSimpleName());
        } else {
            makeCurrentDocument(docLink);
        }
    }

    private void makeCurrentDocument(DocumentLink docLink) {
        try {
            mRepository.changeCurrentDocument(mRepository.loadDocument(docLink.getId()));
            openCurrentDocument();
        } catch (Exception ex) {
            Log.e(TAG, "Exception while loading document for editing.", ex);
            Toast toast = Toast.makeText(getContext(), R.string.generic_unexpected_error, Toast.LENGTH_LONG);
            toast.show();
        }
    }

    private void openCurrentDocument() {
        Activity activity = getActivity();
        if (activity instanceof MainActivity) {
            saveInstanceState();
            ((MainActivity) activity).openFragment(new DocumentFragment());
        } else {
            Log.e(TAG, "Activity holding fragment is not MainActivity!");
        }
    }

    private void showDeleteDialog(DocumentLink docLink) {
        final DeleteDialogFragment deleteDialog = new DeleteDialogFragment();
        deleteDialog.setDialogListener(new DeleteDialogListener(docLink));
        deleteDialog.show(getFragmentManager(), DeleteDialogFragment.class.getSimpleName());
    }

    private void refreshListAfterDelete() {
        mIsLoaded = false;
        mListState = null;
        if (null != mRepository) {
            mRepository.setOnLoadedListener(new DocumentsLoadedListener());
            mRepository.beginLoad();
        }
    }

    private final class DeleteDialogListener implements DeleteDialogFragment.DeleteDialogListener {
        private final DocumentLink mDocumentLink;

        private DeleteDialogListener(DocumentLink documentLink) {
            mDocumentLink = documentLink;
        }

        @Override
        public void onDismiss() {
            if (null == mRepository) {
                mRepository = DocumentsRepository.getInstance(getContext());
            }
            mRepository.deleteDocument(mDocumentLink.getId());
            refreshListAfterDelete();
        }
    }

    private final class EditUnsavedDialogListener implements EditUnsavedDialogFragment.EditUnsavedDialogListener {
        private final DocumentLink mDocumentLink;

        private EditUnsavedDialogListener(DocumentLink documentLink) {
            mDocumentLink = documentLink;
        }

        @Override
        public void onDismiss() {
            makeCurrentDocument(mDocumentLink);
        }
    }

    private final class DocumentsLoadedListener implements DocumentsRepository.OnLoadedListener {
        @Override
        public void onException(Exception ex) {
            Toast toast = Toast.makeText(getContext(), R.string.generic_unexpected_error, Toast.LENGTH_LONG);
            toast.show();
        }

        @Override
        public void onLoaded(List<DocumentLink> list) {
            mDocumentsList = list;
            mIsLoaded = true;

            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    if (null != getActivity()) {
                        initializeList();
                    }
                }
            });
        }
    }
}
