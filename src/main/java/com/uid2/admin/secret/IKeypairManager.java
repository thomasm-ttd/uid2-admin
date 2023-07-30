package com.uid2.admin.secret;

import com.uid2.shared.model.ClientSideKeypair;

public interface IKeypairManager {
    ClientSideKeypair createAndSaveSiteKeypair(int siteId, String contact, boolean disabled) throws Exception;
}
