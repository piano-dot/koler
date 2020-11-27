package com.chooloo.www.callmanager.ui.main;

import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.chooloo.www.callmanager.ui.page.PageContacts;
import com.chooloo.www.callmanager.ui.page.PageFragment;
import com.chooloo.www.callmanager.ui.page.PageRecents;

import timber.log.Timber;

public class MainPagerAdapter extends FragmentPagerAdapter {

    public MainPagerAdapter(FragmentManager fragmentManager) {
        super(fragmentManager);
    }

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public PageFragment getItem(int position) {
        switch (position) {
            case 0:
                Timber.i("GOT RECENTS ITEM");
                return PageRecents.newInstance();
            case 1:
                Timber.i("GOT CONTACTS ITEM");
                return PageContacts.newInstance();
            default:
                return PageContacts.newInstance();
        }
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        switch (position) {
            case 0:
                return "Recents";
            case 1:
                return "Contacts";
            default:
                return "Contacts";
        }
    }
}
