#!/usr/bin/env bash
set -euo pipefail
out=${1:?output directory}
here=$(dirname "$0")
read -r name version _ < <(openssl version)
IFS=. read -r major minor _ <<< "$version"
if [[ $name != OpenSSL ]] || ((major < 3 || (major == 3 && minor < 4))); then
    echo "OpenSSL 3.4 or newer required, found $name $version" >&2
    exit 1
fi
mkdir -p "$out"
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT
export OPENSSL_CONF=/dev/null
dates=(-not_before 20000101000000Z -not_after 99991231235959Z)

rsa_key() {
    python3 -I "${TESTKEY:-$here/testkey.py}" "$2" "$3" > "$tmp/$1.cnf"
    openssl asn1parse -genconf "$tmp/$1.cnf" -out "$out/$1.key" > /dev/null
    openssl pkey -inform DER -in "$out/$1.key" -check -noout > /dev/null
}

ec_key() {
    printf 'asn1=SEQUENCE:key\n[key]\nversion=INTEGER:1\nkey=FORMAT:HEX,OCTETSTRING:%s\ncurve=EXPLICIT:0,OID:prime256v1\n' \
        "$(printf "$2%.0s" $(seq $((64 / ${#2}))))" > "$tmp/$1.cnf"
    openssl asn1parse -genconf "$tmp/$1.cnf" -out "$tmp/$1.der" > /dev/null
    openssl pkcs8 -topk8 -nocrypt -inform DER -in "$tmp/$1.der" -outform DER -out "$out/$1.key"
    openssl pkey -inform DER -in "$out/$1.key" -check -noout > /dev/null
}

cert() {
    openssl req -x509 -new -key "$out/$1.key" -subj "$2" -set_serial "$3" "${dates[@]}" \
        -outform DER -out "$out/$1.crt" "${@:4}" 2> "$tmp/req.log" ||
        { cat "$tmp/req.log" >&2; exit 1; }
}

rsa_key ca CAFEBABE F00DBABE
cert ca "/O=LibreAuto tests/CN=FAKE root, not a real key" 1 \
    -addext basicConstraints=critical,CA:true -addext keyUsage=critical,keyCertSign
rsa_key phone DEADBEEF F00DBEEF
cert phone "/O=LibreAuto tests/CN=FAKE phone, not a real key" 2 \
    -CA "$out/ca.crt" -CAkey "$out/ca.key"
ec_key ec F00D
cert ec "/O=LibreAuto tests/CN=FAKE EC, not a real key" 3 -sigopt nonce-type:1

openssl verify -CAfile <(openssl x509 -inform DER -in "$out/ca.crt") \
    <(openssl x509 -inform DER -in "$out/phone.crt") > /dev/null
