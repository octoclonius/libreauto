package io.github.octoclonius.libreauto.privileged;

interface IPrivilegedService {
    void destroy() = 16777114;

    int getUid() = 1;
}
