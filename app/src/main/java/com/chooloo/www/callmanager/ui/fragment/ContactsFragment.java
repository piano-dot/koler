package com.chooloo.www.callmanager.ui.fragment;

import android.Manifest;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProviders;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.chooloo.www.callmanager.R;
import com.chooloo.www.callmanager.adapter.ContactsAdapter;
import com.chooloo.www.callmanager.adapter.listener.OnItemClickListener;
import com.chooloo.www.callmanager.adapter.listener.OnItemLongClickListener;
import com.chooloo.www.callmanager.database.entity.Contact;
import com.chooloo.www.callmanager.google.FastScroller;
import com.chooloo.www.callmanager.google.FavoritesAndContactsLoader;
import com.chooloo.www.callmanager.ui.FABCoordinator;
import com.chooloo.www.callmanager.ui.activity.MainActivity;
import com.chooloo.www.callmanager.ui.fragment.base.AbsRecyclerViewFragment;
import com.chooloo.www.callmanager.util.CallManager;
import com.chooloo.www.callmanager.util.ContactUtils;
import com.chooloo.www.callmanager.util.Utilities;
import com.chooloo.www.callmanager.viewmodels.SharedDialViewModel;
import com.chooloo.www.callmanager.viewmodels.SharedSearchViewModel;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import timber.log.Timber;

import static android.Manifest.permission.READ_CONTACTS;
import static android.Manifest.permission.WRITE_CONTACTS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

/**
 * A {@link androidx.fragment.app.Fragment} that is heavily influenced by
 * Google's default dial app - <a href="ContactsFragment">https://android.googlesource.com/platform/packages/apps/Dialer/+/refs/heads/master/java/com/android/dialer/contactsfragment/ContactsFragment.java</a>
 */
public class ContactsFragment extends AbsRecyclerViewFragment implements
        LoaderManager.LoaderCallbacks<Cursor>,
        FABCoordinator.FABDrawableCoordination,
        FABCoordinator.OnFABClickListener,
        View.OnScrollChangeListener,
        ContactsAdapter.OnContactSelectedListener, OnItemClickListener, OnItemLongClickListener {

    private static final int LOADER_ID = 1;
    private static final String ARG_PHONE_NUMBER = "phone_number";
    private static final String ARG_CONTACT_NAME = "contact_name";
    // View Binds
    @BindView(R.id.fast_scroller) FastScroller mFastScroller;
    @BindView(R.id.refresh_layout) SwipeRefreshLayout mRefreshLayout;
    @BindView(R.id.enable_permission_btn) Button mEnablePermissionButton;
    @BindView(R.id.item_header) TextView mAnchoredHeader;
    @BindView(R.id.empty_state) View mEmptyState;
    @BindView(R.id.empty_title) TextView mEmptyTitle;
    @BindView(R.id.empty_desc) TextView mEmptyDesc;
    LinearLayoutManager mLayoutManager;
    ContactsAdapter mContactsAdapter;
    // View Models
    private SharedDialViewModel mSharedDialViewModel;
    private SharedSearchViewModel mSharedSearchViewModel;

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Dialer View Model
        mSharedDialViewModel = ViewModelProviders.of(getActivity()).get(SharedDialViewModel.class);
        mSharedDialViewModel.getNumber().observe(this, s -> {
            if (isLoaderRunning()) {
                Bundle args = new Bundle();
                args.putString(ARG_PHONE_NUMBER, s);
                LoaderManager.getInstance(this).restartLoader(LOADER_ID, args, this);
            }
        });

        // Search Bar View Model
        mSharedSearchViewModel = ViewModelProviders.of(getActivity()).get(SharedSearchViewModel.class);
        mSharedSearchViewModel.getText().observe(this, t -> {
            if (isLoaderRunning()) {
                Bundle args = new Bundle();
                args.putString(ARG_CONTACT_NAME, t);
                LoaderManager.getInstance(this).restartLoader(LOADER_ID, args, this);
            }
        });

        tryRunningLoader();
    }

    @Override
    protected void onFragmentReady() {

        checkShowButton();
        mEnablePermissionButton.setText(getString(R.string.button_enable_contact_permission));

        mLayoutManager =
                new LinearLayoutManager(getContext()) {
                    @Override
                    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
                        super.onLayoutChildren(recycler, state);
                        int itemsShown = findLastVisibleItemPosition() - findFirstVisibleItemPosition() + 1;
                        if (mContactsAdapter.getItemCount() > itemsShown) {
                            mFastScroller.setVisibility(View.VISIBLE);
                            mRecyclerView.setOnScrollChangeListener(ContactsFragment.this);
                        } else {
                            mFastScroller.setVisibility(View.GONE);
                        }
                    }
                };

        // The list adapter
        mContactsAdapter = new ContactsAdapter(getContext(), null, this, this);
        mContactsAdapter.setOnContactSelectedListener(this);

        // Recycle View
        mRecyclerView.setAdapter(mContactsAdapter);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                switch (newState) {
                    case RecyclerView.SCROLL_STATE_IDLE:
                        mSharedDialViewModel.setIsOutOfFocus(false);
                        break;
                    case RecyclerView.SCROLL_STATE_DRAGGING:
                        mSharedDialViewModel.setIsOutOfFocus(true);
                        mSharedSearchViewModel.setIsFocused(false);
                        break;
                    case RecyclerView.SCROLL_STATE_SETTLING:
                        mSharedDialViewModel.setIsOutOfFocus(true);
                        mSharedSearchViewModel.setIsFocused(false);
                    default:
                        mSharedDialViewModel.setIsOutOfFocus(false);
                }
            }
        });

        // Refresh Layout
        mRefreshLayout.setOnRefreshListener(() -> {
            LoaderManager.getInstance(ContactsFragment.this).restartLoader(LOADER_ID, null, ContactsFragment.this);
            tryRunningLoader();
        });

        mEmptyTitle.setText(R.string.empty_contact_title);
        mEmptyDesc.setText(R.string.empty_contact_desc);
    }

    @Override
    public void onResume() {
        super.onResume();
        tryRunningLoader();
    }

    /*
     * When our recycler view updates, we need to ensure that our row headers and anchored header
     * are in the correct state.
     *
     * The general rule is, when the row headers are shown, our anchored header is hidden. When the
     * recycler view is scrolling through a sublist that has more than one element, we want to show
     * out anchored header, to create the illusion that our row header has been anchored. In all
     * other situations, we want to hide the anchor because that means we are transitioning between
     * two sublists.
     */
    @Override
    public void onScrollChange(View v, int scrollX, int scrollY, int oldScrollX, int oldScrollY) {
        mFastScroller.updateContainerAndScrollBarPosition(mRecyclerView);
        int firstVisibleItem = mLayoutManager.findFirstVisibleItemPosition();
        int firstCompletelyVisible = mLayoutManager.findFirstCompletelyVisibleItemPosition();
        if (firstCompletelyVisible == RecyclerView.NO_POSITION) {
            // No items are visible, so there are no headers to update.
            return;
        }
        String anchoredHeaderString = mContactsAdapter.getHeaderString(firstCompletelyVisible);

        // If the user swipes to the top of the list very quickly, there is some strange behavior
        // between this method updating headers and adapter#onBindViewHolder updating headers.
        // To overcome this, we refresh the headers to ensure they are correct.
        if (firstVisibleItem == firstCompletelyVisible && firstVisibleItem == 0) {
            mContactsAdapter.refreshHeaders();
            mAnchoredHeader.setVisibility(View.INVISIBLE);
        } else {
            if (mContactsAdapter.getHeaderString(firstVisibleItem).equals(anchoredHeaderString)) {
                mAnchoredHeader.setText(anchoredHeaderString);
                mAnchoredHeader.setVisibility(View.VISIBLE);
                getContactHolder(firstVisibleItem).getHeaderView().setVisibility(View.INVISIBLE);
                getContactHolder(firstCompletelyVisible).getHeaderView().setVisibility(View.INVISIBLE);
            } else {
                mAnchoredHeader.setVisibility(View.INVISIBLE);
                getContactHolder(firstVisibleItem).getHeaderView().setVisibility(View.VISIBLE);
                getContactHolder(firstCompletelyVisible).getHeaderView().setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    public void onContactSelected(String normPhoneNumber) {
        if (normPhoneNumber == null) return;
        CallManager.call(getContext(), normPhoneNumber);
    }

    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, @Nullable Bundle args) {
        String contactName = args != null && args.containsKey(ARG_CONTACT_NAME) ? args.getString(ARG_CONTACT_NAME) : null;
        String phoneNumber = args != null && args.containsKey(ARG_PHONE_NUMBER) ? args.getString(ARG_PHONE_NUMBER) : null;
        return new FavoritesAndContactsLoader(getContext(), phoneNumber, contactName);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        setData(data);
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
        mContactsAdapter.changeCursor(null);
    }

    // -- Loader -- //

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        tryRunningLoader();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
//        return inflater.inflate(R.layout.fragment_contacts, container, false);
        View view = inflater.inflate(R.layout.fragment_items, container, false);
        ButterKnife.bind(this, view);
        return view;
    }

    @Override
    public void onRightClick() {
        ((MainActivity) getActivity()).expandDialer(true);
    }

    @Override
    public void onLeftClick() {
        ((MainActivity) getActivity()).toggleSearchBar();
    }

    @Override
    public void onItemClick(RecyclerView.ViewHolder holder, Object data) {
        Contact contact = (Contact) data;
        showContactPopup(contact);
    }

    @Override
    public void onItemLongClick(RecyclerView.ViewHolder holder, Object data) {

    }

    @Override
    public int[] getIconsResources() {
        return new int[]{
                R.drawable.ic_dialpad_black_24dp,
                R.drawable.ic_search_black_24dp
        };
    }

    private ContactsAdapter.ContactHolder getContactHolder(int position) {
        return ((ContactsAdapter.ContactHolder) mRecyclerView.findViewHolderForAdapterPosition(position));
    }

    private void setData(Cursor data) {
        mContactsAdapter.changeCursor(data);
        mFastScroller.setup(mContactsAdapter, mLayoutManager);
        if (mRefreshLayout.isRefreshing()) mRefreshLayout.setRefreshing(false);

        boolean isDataExist = data != null && data.getCount() > 0;
        mRecyclerView.setVisibility(isDataExist ? View.VISIBLE : View.GONE);
        mEmptyState.setVisibility(isDataExist ? View.GONE : View.VISIBLE);
    }

    /**
     * Checking whither to show the "enable contacts" button
     */
    public void checkShowButton() {
        if (Utilities.checkPermissionGranted(getContext(), READ_CONTACTS, false)) {
            mEnablePermissionButton.setVisibility(View.GONE);
        } else {
            mEmptyTitle.setText(R.string.empty_contact_persmission_title);
            mEmptyDesc.setText(R.string.empty_contact_persmission_desc);
            mEnablePermissionButton.setVisibility(View.VISIBLE);
        }
    }

    // -- FABCoordinator.OnFabClickListener -- //

    /**
     * Checks for the required permission in order to run the loader
     */
    private void tryRunningLoader() {
        if (!isLoaderRunning() && Utilities.checkPermissionGranted(getContext(), READ_CONTACTS, true)) {
            runLoader();
            mEnablePermissionButton.setVisibility(View.GONE);
        }
    }

    /**
     * Runs the loader
     */
    private void runLoader() {
        LoaderManager.getInstance(this).initLoader(LOADER_ID, null, this);
    }

    /**
     * Checks whither the loader is currently running
     * (meaning the page is refreshing)
     *
     * @return boolean
     */
    private boolean isLoaderRunning() {
        Loader loader = LoaderManager.getInstance(this).getLoader(LOADER_ID);
        return loader != null;
    }

    @OnClick(R.id.enable_permission_btn)
    public void askForContactsPermission() {
        Utilities.askForPermission(getActivity(), READ_CONTACTS);
    }


    // -- FABCoordinator.FABDrawableCoordinator -- //

    /**
     * Shows a pop up window (dialog) with the contact's information
     *
     * @param contact
     */
    private void showContactPopup(Contact contact) {

        // Initiate the dialog
        Dialog contactDialog = new Dialog(getContext());
        contactDialog.setContentView(R.layout.contact_popup_view);

        // Views declarations
        ConstraintLayout popupLayout;
        TextView contactName, contactNumber, contactDate;
        ImageView contactPhoto, contactPhotoPlaceholder;
        ImageButton callButton, editButton, deleteButton, infoButton, addButton, smsButton, favButton;

        popupLayout = contactDialog.findViewById(R.id.contact_popup_layout);

        contactPhoto = contactDialog.findViewById(R.id.contact_popup_photo);
        contactPhotoPlaceholder = contactDialog.findViewById(R.id.contact_popup_photo_placeholder);

        contactName = contactDialog.findViewById(R.id.contact_popup_name);
        contactNumber = contactDialog.findViewById(R.id.contact_popup_number);
        contactDate = contactDialog.findViewById(R.id.contact_popup_date);

        callButton = contactDialog.findViewById(R.id.contact_popup_button_call);
        editButton = contactDialog.findViewById(R.id.contact_popup_button_edit);
        deleteButton = contactDialog.findViewById(R.id.contact_popup_button_delete);
        infoButton = contactDialog.findViewById(R.id.contact_popup_button_info);
        addButton = contactDialog.findViewById(R.id.contact_popup_button_add);
        smsButton = contactDialog.findViewById(R.id.contact_popup_button_sms);
        favButton = contactDialog.findViewById(R.id.contact_popup_button_fav);

        if (contact.getName() != null) {
            contactName.setText(contact.getName());
            contactNumber.setText(Utilities.formatPhoneNumber(contact.getMainPhoneNumber()));
            infoButton.setVisibility(View.VISIBLE);
            editButton.setVisibility(View.VISIBLE);
            if (contact.getIsFavorite())
                favButton.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_star_black_24dp));
        } else {
            infoButton.setVisibility(View.GONE);
            editButton.setVisibility(View.GONE);
            addButton.setVisibility(View.VISIBLE);
            contactName.setText(Utilities.formatPhoneNumber(contact.getMainPhoneNumber()));
            contactNumber.setVisibility(View.GONE);
        }

        if (contact.getPhotoUri() == null || contact.getPhotoUri().isEmpty()) {
            contactPhoto.setVisibility(View.GONE);
            contactPhotoPlaceholder.setVisibility(View.VISIBLE);
        } else {
            contactPhoto.setVisibility(View.VISIBLE);
            contactPhotoPlaceholder.setVisibility(View.GONE);
            contactPhoto.setImageURI(Uri.parse(contact.getPhotoUri()));
        }

        callButton.setOnClickListener(v -> {
            Timber.i("MAIN PHONE NUMBER: " + contact.getMainPhoneNumber());
            CallManager.call(this.getContext(), contact.getMainPhoneNumber());
        });

        editButton.setOnClickListener(v -> {
            ContactUtils.openContactToEdit(getActivity(), contact.getContactId());
        });

        infoButton.setOnClickListener(v -> {
            ContactUtils.openContact(getActivity(), contact.getContactId());
        });

        smsButton.setOnClickListener(v -> {
            Utilities.openSmsWithNumber(getActivity(), contact.getMainPhoneNumber());
        });

        favButton.setOnClickListener(v -> {
            if (ActivityCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                if (contact.getIsFavorite()) {
                    contact.setIsFavorite(false);
                    favButton.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_star_outline_black_24dp));
                    ContactUtils.setContactIsFavorite(getActivity(), Long.toString(contact.getContactId()), false);
                } else {
                    contact.setIsFavorite(true);
                    favButton.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_star_black_24dp));
                    ContactUtils.setContactIsFavorite(getActivity(), Long.toString(contact.getContactId()), true);
                }
            } else {
                Utilities.askForPermission(getActivity(), WRITE_CONTACTS);
                Toast.makeText(getContext(), "I dont have the permission to do that :(", Toast.LENGTH_LONG).show();
            }
        });

        deleteButton.setOnClickListener(v -> {
            if (ContextCompat.checkSelfPermission(getContext(), Manifest.permission.WRITE_CONTACTS) == PERMISSION_GRANTED) {
                ContactUtils.deleteContact(getActivity(), contact.getContactId());
                contactDialog.dismiss();
            } else {
                Toast.makeText(getContext(), "I dont have the permission", Toast.LENGTH_LONG).show();
                contactDialog.dismiss();
            }
        });

        popupLayout.setElevation(20);

        contactDialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        contactDialog.show();

    }

    /**
     * An enum for the different types of headers that be inserted at position 0 in the list.
     */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Header.NONE, Header.STAR})
    public @interface Header {
        int NONE = 0;
        int STAR = 1;
    }
}
