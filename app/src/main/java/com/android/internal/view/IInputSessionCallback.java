/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: frameworks/base/core/java/com/android/internal/view/IInputSessionCallback.aidl
 */
package com.android.internal.view;

/**
 * Helper interface for IInputMethod to allow the input method to notify the client when a new
 * session has been created.
 * {@hide}
 */
public interface IInputSessionCallback extends android.os.IInterface {
    /**
     * Local-side IPC implementation stub class.
     */
    public static abstract class Stub extends android.os.Binder implements IInputSessionCallback {
        private static final String DESCRIPTOR = "com.android.internal.view.IInputSessionCallback";

        /**
         * Construct the stub at attach it to the interface.
         */
        public Stub() {
            this.attachInterface(this, DESCRIPTOR);
        }

        /**
         * Cast an IBinder object into an com.android.internal.view.IInputSessionCallback interface,
         * generating a proxy if needed.
         */
        public static IInputSessionCallback asInterface(android.os.IBinder obj) {
            if ((obj == null)) {
                return null;
            }
            android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
            if (((iin != null) && (iin instanceof IInputSessionCallback))) {
                return ((IInputSessionCallback) iin);
            }
            return new Proxy(obj);
        }

        @Override
        public android.os.IBinder asBinder() {
            return this;
        }

        @Override
        public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException {
            switch (code) {
                case INTERFACE_TRANSACTION: {
                    reply.writeString(DESCRIPTOR);
                    return true;
                }
                case TRANSACTION_sessionCreated: {
                    data.enforceInterface(DESCRIPTOR);
                    IInputMethodSession _arg0;
                    _arg0 = IInputMethodSession.Stub.asInterface(data.readStrongBinder());
                    this.sessionCreated(_arg0);
                    return true;
                }
            }
            return super.onTransact(code, data, reply, flags);
        }

        private static class Proxy implements IInputSessionCallback {
            private android.os.IBinder mRemote;

            Proxy(android.os.IBinder remote) {
                mRemote = remote;
            }

            @Override
            public android.os.IBinder asBinder() {
                return mRemote;
            }

            public String getInterfaceDescriptor() {
                return DESCRIPTOR;
            }

            @Override
            public void sessionCreated(IInputMethodSession session) throws android.os.RemoteException {
                android.os.Parcel _data = android.os.Parcel.obtain();
                try {
                    _data.writeInterfaceToken(DESCRIPTOR);
                    _data.writeStrongBinder((((session != null)) ? (session.asBinder()) : (null)));
                    mRemote.transact(Stub.TRANSACTION_sessionCreated, _data, null, android.os.IBinder.FLAG_ONEWAY);
                } finally {
                    _data.recycle();
                }
            }
        }

        static final int TRANSACTION_sessionCreated = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
    }

    public void sessionCreated(IInputMethodSession session) throws android.os.RemoteException;
}
