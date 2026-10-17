import sys

E = 65537
BITS = 2048


def is_prime(n):
    small = (2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37, 41, 43, 47, 53)
    if any(n % p == 0 for p in small):
        return False
    d, s = n - 1, 0
    while d % 2 == 0:
        d, s = d // 2, s + 1
    for a in small:
        x = pow(a, d, n)
        if x in (1, n - 1):
            continue
        for _ in range(s - 1):
            x = pow(x, 2, n)
            if x == n - 1:
                break
        else:
            return False
    return True


def next_prime(n):
    n |= 1
    while not (is_prime(n) and (n - 1) % E != 0):
        n += 2
    return n


def repeat(word, bits):
    return int((word * (bits // 4))[: bits // 4], 16)


word, p_word = sys.argv[1:]
p = next_prime(repeat(p_word, BITS // 2))
q = next_prime(repeat(word, BITS) // p + 1)
assert p.bit_length() == q.bit_length() == BITS // 2, "P_WORD must be larger than WORD"
n = p * q
d = pow(E, -1, (p - 1) * (q - 1))

print(f"""asn1=SEQUENCE:pkcs8
[pkcs8]
version=INTEGER:0
algorithm=SEQUENCE:rsa
key=OCTWRAP,SEQUENCE:key
[rsa]
oid=OID:rsaEncryption
parameters=NULL
[key]
version=INTEGER:0
modulus=INTEGER:0x{n:X}
publicExponent=INTEGER:{E}
privateExponent=INTEGER:0x{d:X}
prime1=INTEGER:0x{p:X}
prime2=INTEGER:0x{q:X}
exponent1=INTEGER:0x{d % (p - 1):X}
exponent2=INTEGER:0x{d % (q - 1):X}
coefficient=INTEGER:0x{pow(q, -1, p):X}""")
