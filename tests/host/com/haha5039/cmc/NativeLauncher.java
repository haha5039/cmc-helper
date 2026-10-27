// Host-only type adapter; native Activity launching is not exercised here.
package com.haha5039.cmc;
final class NativeLauncher {
    static int open(boolean developer,int task) { throw new UnsupportedOperationException(); }
    static int closeCmcSettings() { throw new UnsupportedOperationException(); }
}
