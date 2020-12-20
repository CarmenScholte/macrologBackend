package slt.connectivity.oath2;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@SuppressWarnings("all")
public class Oath2Token {

    private Long expires_at;
    private Long expires_in;
    private String refresh_token;
    private String access_token;

}
