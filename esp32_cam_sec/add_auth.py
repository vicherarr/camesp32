import sys
content = open("src/server.rs").read()
macro = """
macro_rules! require_auth {
    ($req:expr) => {
        if $req.header("Authorization") != Some("Basic YWRtaW46MTIzNDU2") {
            let mut response = $req.into_response(401, Some("Unauthorized"), &[("WWW-Authenticate", "Basic realm=\\"CamSec\\"")])?;
            response.write_all(b"Unauthorized")?;
            return Ok::<(), anyhow::Error>(());
        }
    };
}
"""

if "require_auth" not in content:
    content = content.replace("impl WebServer {", macro + "\nimpl WebServer {")

lines = content.split('\n')
out = []
for line in lines:
    out.append(line)
    if "server.fn_handler(" in line and "require_auth" not in line:
        indent = line[:len(line) - len(line.lstrip())]
        out.append(indent + "    require_auth!(request);")

with open("src/server.rs", "w") as f:
    f.write("\n".join(out))
