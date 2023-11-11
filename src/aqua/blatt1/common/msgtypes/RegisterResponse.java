package aqua.blatt1.common.msgtypes;

import java.io.Serializable;

@SuppressWarnings("serial")
public record RegisterResponse(String id) implements Serializable {
}
