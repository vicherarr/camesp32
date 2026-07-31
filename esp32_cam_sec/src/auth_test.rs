use esp_idf_svc::http::server::Request;
use esp_idf_svc::http::server::EspHttpConnection;
pub fn check_auth(req: &Request<&mut EspHttpConnection>) -> bool {
    req.header("Authorization") == Some("Basic YWRtaW46MTIzNDU2")
}
