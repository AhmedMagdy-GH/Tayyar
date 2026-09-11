package com.tayyar.common.api;

import java.io.IOException;

final class RequestBodyTooLargeException extends IOException {
    RequestBodyTooLargeException() {
        super("Request body exceeded configured limit");
    }
}
