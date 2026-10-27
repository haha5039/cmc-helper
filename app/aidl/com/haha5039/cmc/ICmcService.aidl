package com.haha5039.cmc;

interface ICmcService {
    void destroy() = 16777114;
    int getUid() = 1;
    String writeRole(@nullable String role) = 2;
    String prepare(boolean enable, String requestId) = 3;
    int launchNative(String requestId, boolean developer, int taskId) = 4;
    String prepareRole(String role, String requestId) = 5;
    String openDeveloper(String requestId) = 6;
    String refreshCmcUi() = 7;
}
