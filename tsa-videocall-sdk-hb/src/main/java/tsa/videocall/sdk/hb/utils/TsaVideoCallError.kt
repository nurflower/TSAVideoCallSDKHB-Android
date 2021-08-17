package tsa.videocall.sdk.hb.utils

import kotlin.Exception

class TsaVideoCallError(private val errorType: ErrorType, private val errorCode: ErrorCode, private val message: String? = null, private val exception: Exception? = null) {

    public enum class ErrorType{
        SessionError{
            override val value: String
                get() = "SessionError"
        },
        PublisherError{
            override val value: String
                get() = "PublisherError"
        },
        SubscriberError{
            override val value: String
                get() = "SubscriberError"
        };

        abstract val value: String
    }


    public enum class ErrorCode{
        UnknownError{
            override val value: Int
                get() = -1
        },
        InvalidSessionId{
            override val value: Int
                get() = 1001
        },
        MediaServerError{
            override val value: Int
                get() = 2001
        },
        CallCheckError{
            override val value: Int
                get() = 2002
        },
        CallCheckRequestError{
            override val value: Int
                get() = 2003
        },
        CallStartError{
            override val value: Int
                get() = 2004
        },
        CallStartRequestError{
            override val value: Int
                get() = 2005
        },
        ConnectionFailed{
            override val value: Int
                get() = 1002
        };
        abstract val value: Int
    }

    public fun getMessage(): String?{
        return this.message
    }

    public fun getException(): Exception?{
        return this.exception
    }

    public fun getErrorType(): ErrorType {
        return this.errorType
    }

    public fun getErrorCode(): ErrorCode {
        return this.errorCode
    }

}