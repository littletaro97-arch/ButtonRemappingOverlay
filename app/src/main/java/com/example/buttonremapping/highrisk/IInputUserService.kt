package com.example.buttonremapping.highrisk

import android.os.Binder
import android.os.IBinder
import android.os.IInterface
import android.os.Parcel

interface IInputUserService : IInterface {
    fun injectTap(x: Int, y: Int, displayId: Int): Boolean

    fun destroy()

    fun exit()

    abstract class Stub : Binder(), IInputUserService {
        init {
            attachInterface(this, DESCRIPTOR)
        }

        override fun asBinder(): IBinder = this

        override fun onTransact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean {
            if (code == INTERFACE_TRANSACTION) {
                reply?.writeString(DESCRIPTOR)
                return true
            }
            return when (code) {
                TRANSACTION_DESTROY -> {
                    data.enforceInterface(DESCRIPTOR)
                    destroy()
                    true
                }

                TRANSACTION_EXIT -> {
                    data.enforceInterface(DESCRIPTOR)
                    exit()
                    reply?.writeNoException()
                    true
                }

                TRANSACTION_INJECT_TAP -> {
                    data.enforceInterface(DESCRIPTOR)
                    val result = injectTap(data.readInt(), data.readInt(), data.readInt())
                    reply?.writeNoException()
                    reply?.writeInt(if (result) 1 else 0)
                    true
                }

                else -> super.onTransact(code, data, reply, flags)
            }
        }

        companion object {
            private const val DESCRIPTOR = "com.example.buttonremapping.highrisk.IInputUserService"
            private const val TRANSACTION_EXIT = 1
            private const val TRANSACTION_INJECT_TAP = 2
            private const val TRANSACTION_DESTROY = 16777114

            fun asInterface(binder: IBinder?): IInputUserService? {
                if (binder == null) return null
                val local = binder.queryLocalInterface(DESCRIPTOR)
                if (local is IInputUserService) return local
                return Proxy(binder)
            }
        }
    }

    private class Proxy(private val remote: IBinder) : IInputUserService {
        override fun asBinder(): IBinder = remote

        override fun injectTap(x: Int, y: Int, displayId: Int): Boolean {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            return try {
                data.writeInterfaceToken(DESCRIPTOR)
                data.writeInt(x)
                data.writeInt(y)
                data.writeInt(displayId)
                remote.transact(TRANSACTION_INJECT_TAP, data, reply, 0)
                reply.readException()
                reply.readInt() != 0
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        override fun destroy() {
            val data = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TRANSACTION_DESTROY, data, null, IBinder.FLAG_ONEWAY)
            } finally {
                data.recycle()
            }
        }

        override fun exit() {
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(DESCRIPTOR)
                remote.transact(TRANSACTION_EXIT, data, reply, 0)
                reply.readException()
            } finally {
                data.recycle()
                reply.recycle()
            }
        }

        companion object {
            private const val DESCRIPTOR = "com.example.buttonremapping.highrisk.IInputUserService"
            private const val TRANSACTION_EXIT = 1
            private const val TRANSACTION_INJECT_TAP = 2
            private const val TRANSACTION_DESTROY = 16777114
        }
    }
}
