// Host-only adapter: deliberately not an Android runtime implementation.
package com.haha5039.cmc;
import android.os.IBinder;
import android.os.RemoteException;
public interface ICmcService {
    IBinder asBinder();
    void destroy();
    int getUid() throws RemoteException;
    String writeRole(String role) throws RemoteException;
    String prepare(boolean enable,String request) throws RemoteException;
    int launchNative(String request,boolean developer,int task) throws RemoteException;
    String prepareRole(String role,String request) throws RemoteException;
    String openDeveloper(String request) throws RemoteException;
    String refreshCmcUi() throws RemoteException;
    String applyNativeRole(String role,String request) throws RemoteException;
    abstract class Stub implements ICmcService {
        public IBinder asBinder() { return null; }
        public static ICmcService asInterface(IBinder binder) { return (ICmcService) binder; }
    }
}
