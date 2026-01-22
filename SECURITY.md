# Security Advisory

## Dependency Updates - January 2026

### Overview

This document tracks security vulnerabilities found in dependencies and their remediation.

## Resolved Vulnerabilities

### 1. cryptography - NULL Pointer Dereference (CVE-2024-XXXXX)

**Severity**: High

**Affected Versions**: 
- cryptography >= 38.0.0, < 42.0.4

**Description**: 
NULL pointer dereference in `pkcs12.serialize_key_and_certificates` when called with a non-matching certificate and private key with an hmac_hash override.

**Resolution**: 
- ✅ Updated to cryptography >= 42.0.4
- Patched in: version 42.0.4

### 2. cryptography - Bleichenbacher Timing Oracle Attack

**Severity**: High

**Affected Versions**: 
- cryptography < 42.0.0

**Description**: 
Python Cryptography package vulnerable to Bleichenbacher timing oracle attack against RSA decryption.

**Resolution**: 
- ✅ Updated to cryptography >= 42.0.4 (exceeds minimum 42.0.0)
- Patched in: version 42.0.0

### 3. Pillow - Buffer Overflow Vulnerability

**Severity**: Medium

**Affected Versions**: 
- Pillow < 10.3.0

**Description**: 
Buffer overflow vulnerability in Pillow image processing library.

**Resolution**: 
- ✅ Updated to Pillow >= 10.3.0
- Patched in: version 10.3.0

## Current Dependency Versions

```
PyBluez==0.23
Pillow>=10.3.0      # Fixed buffer overflow (was 10.1.0)
cryptography>=42.0.4 # Fixed NULL pointer & timing oracle (was 41.0.7)
```

## Verification

To verify you have the patched versions:

```bash
pip list | grep -E "(cryptography|Pillow)"
```

Expected output:
```
cryptography  42.0.4 or higher
Pillow       10.3.0 or higher
```

## Recommendations

1. **Always update to latest patched versions** when security vulnerabilities are discovered
2. **Use version constraints** with `>=` rather than `==` for security libraries to allow automatic patch updates
3. **Monitor security advisories** for all dependencies regularly
4. **Run security scanners** like `pip-audit` or `safety` periodically

## Installation with Patched Versions

```bash
# Update requirements
pip install -r requirements.txt --upgrade

# Or install directly
pip install "cryptography>=42.0.4" "Pillow>=10.3.0"
```

## Security Scanning

This project uses multiple security scanning tools:

- ✅ **CodeQL** - Static analysis for code vulnerabilities
- ✅ **Code Review** - Automated code review for security issues  
- ✅ **Dependency Scanning** - Manual review of dependency vulnerabilities
- ✅ **GitHub Advisory Database** - Check for known vulnerabilities (via gh-advisory-database tool)

## Reporting Security Issues

If you discover a security vulnerability in Sierra Messenger:

1. **Do NOT** open a public GitHub issue
2. Email the maintainer privately
3. Include detailed information about the vulnerability
4. Allow time for a fix before public disclosure

## Security Best Practices for Users

1. **Keep dependencies updated**: Run `pip install --upgrade -r requirements.txt` regularly
2. **Use virtual environments**: Isolate Sierra Messenger dependencies
3. **Verify downloads**: Check package hashes when installing
4. **Monitor advisories**: Watch GitHub security advisories for this repository
5. **Use in trusted environments**: Bluetooth communication is not encrypted by default

## Changelog

### 2026-01-22
- ✅ Updated cryptography from 41.0.7 to >= 42.0.4 (fixes 2 CVEs)
- ✅ Updated Pillow from 10.1.0 to >= 10.3.0 (fixes buffer overflow)
- ✅ Changed version constraints from `==` to `>=` for security libraries

### 2026-01-22 (Initial Release)
- Initial security review completed
- CodeQL scan: 0 vulnerabilities
- All identified issues in application code fixed

## References

- [Python Cryptography Project](https://cryptography.io/)
- [Pillow Security Advisories](https://pillow.readthedocs.io/en/stable/releasenotes/)
- [PyPA Advisory Database](https://github.com/pypa/advisory-database)

---

**Last Updated**: 2026-01-22  
**Status**: All known vulnerabilities resolved ✅
