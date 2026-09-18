/*
 * RMS (RecordStore) persistence for TOTP accounts.
 * Stores serialized Account objects as individual records.
 */
package com.twofa.store;

import javax.microedition.rms.RecordStore;
import javax.microedition.rms.RecordEnumeration;
import javax.microedition.rms.RecordStoreException;
import javax.microedition.rms.RecordStoreNotFoundException;

import java.util.Vector;

/**
 * Manages persistent storage of Account entries using J2ME RMS.
 * Each account is stored as a single record in a RecordStore.
 */
public final class AccountStore {

    private static final String STORE_NAME = "2FA_Accounts";

    private AccountStore() {
        // static utility class
    }

    /**
     * Load all accounts from RMS.
     * Returns an empty Vector if no accounts stored yet.
     */
    public static Vector loadAll() {
        Vector accounts = new Vector();
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            RecordEnumeration re = rs.enumerateRecords(null, null, false);
            while (re.hasNextElement()) {
                int recId = re.nextRecordId();
                byte[] data = rs.getRecord(recId);
                if (data != null && data.length > 0) {
                    String str = new String(data);
                    Account acct = Account.deserialize(str);
                    if (acct != null) {
                        accounts.addElement(acct);
                    }
                }
            }
        } catch (RecordStoreException e) {
            // Return whatever we loaded so far
        } finally {
            closeStore(rs);
        }
        return accounts;
    }

    /**
     * Save all accounts to RMS (clears existing store first).
     */
    public static void saveAll(Vector accounts) {
        RecordStore rs = null;
        try {
            // Delete old store and recreate for simplicity
            RecordStore.deleteRecordStore(STORE_NAME);
        } catch (Exception e) {
            // Store may not exist yet — fine
        }

        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            for (int i = 0; i < accounts.size(); i++) {
                Account acct = (Account) accounts.elementAt(i);
                byte[] data = acct.serialize().getBytes();
                rs.addRecord(data, 0, data.length);
            }
        } catch (RecordStoreException e) {
            // Best effort
        } finally {
            closeStore(rs);
        }
    }

    /**
     * Add a single account (appends to existing store).
     */
    public static void addAccount(Account acct) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(STORE_NAME, true);
            byte[] data = acct.serialize().getBytes();
            rs.addRecord(data, 0, data.length);
        } catch (RecordStoreException e) {
            // ignore
        } finally {
            closeStore(rs);
        }
    }

    /**
     * Delete an account by index (re-saves all without that entry).
     */
    public static void deleteAccount(int index) {
        Vector accounts = loadAll();
        if (index >= 0 && index < accounts.size()) {
            accounts.removeElementAt(index);
            saveAll(accounts);
        }
    }

    /**
     * Delete all stored accounts.
     */
    public static void clearAll() {
        try {
            RecordStore.deleteRecordStore(STORE_NAME);
        } catch (Exception e) {
            // ignore
        }
    }

    // --- Global settings (clock offset) stored in a separate RMS ---

    private static final String SETTINGS_STORE = "2FA_Settings";
    private static final int OFFSET_RECORD_ID = 1;

    /**
     * Load the global clock offset in seconds.
     * Returns 0 if not set.
     */
    public static int loadClockOffset() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(SETTINGS_STORE, true);
            if (rs.getNumRecords() > 0) {
                byte[] data = rs.getRecord(OFFSET_RECORD_ID);
                if (data != null) {
                    return Integer.parseInt(new String(data));
                }
            }
        } catch (Exception e) {
            // default
        } finally {
            closeStore(rs);
        }
        return 0;
    }

    /**
     * Save the global clock offset in seconds.
     */
    public static void saveClockOffset(int offsetSec) {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore(SETTINGS_STORE, true);
            byte[] data = String.valueOf(offsetSec).getBytes();
            if (rs.getNumRecords() == 0) {
                rs.addRecord(data, 0, data.length);
            } else {
                rs.setRecord(OFFSET_RECORD_ID, data, 0, data.length);
            }
        } catch (Exception e) {
            // ignore
        } finally {
            closeStore(rs);
        }
    }

    private static void closeStore(RecordStore rs) {
        if (rs != null) {
            try {
                rs.closeRecordStore();
            } catch (Exception e) {
                // ignore
            }
        }
    }
}
