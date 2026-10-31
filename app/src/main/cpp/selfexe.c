// Faz /proc/self/exe apontar para o executável real quando o termux-exec roda um binário
// via /system/bin/linker64 (necessário em apps que miram o API 29+).
//
// Sem isto, std::env::current_exe() do Rust devolve o caminho do linker, e o Codex deixa
// de encontrar o codex-code-mode-host ao lado do próprio executável.
//
// O termux-exec exporta TERMUX_EXEC__PROC_SELF_EXE com o caminho absoluto do ELF que está
// sendo executado. Esta biblioteca entra por LD_PRELOAD e intercepta readlink/readlinkat.
//
// Propositalmente não inclui <unistd.h>, para não conflitar com as declarações com
// fortificação da bionic.
#define _GNU_SOURCE
#include <dlfcn.h>
#include <errno.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>

extern pid_t getpid(void);

#define SELF_EXE_ENV "TERMUX_EXEC__PROC_SELF_EXE"
#define EXPORT __attribute__((visibility("default")))

static int is_self_exe(const char *path) {
    if (path == NULL) return 0;
    if (strcmp(path, "/proc/self/exe") == 0) return 1;
    char own[48];
    snprintf(own, sizeof(own), "/proc/%d/exe", (int) getpid());
    return strcmp(path, own) == 0;
}

// Copia o caminho real para o buffer, como readlink (sem terminador nulo).
static ssize_t copy_real_exe(char *buf, size_t size) {
    const char *real = getenv(SELF_EXE_ENV);
    if (real == NULL || real[0] != '/') return -1;
    size_t length = strlen(real);
    if (length > size) length = size;
    memcpy(buf, real, length);
    return (ssize_t) length;
}

typedef ssize_t (*readlink_fn)(const char *, char *, size_t);
typedef ssize_t (*readlinkat_fn)(int, const char *, char *, size_t);

EXPORT ssize_t readlink(const char *path, char *buf, size_t size) {
    if (is_self_exe(path)) {
        ssize_t copied = copy_real_exe(buf, size);
        if (copied >= 0) return copied;
    }
    static readlink_fn real_fn = NULL;
    if (real_fn == NULL) real_fn = (readlink_fn) dlsym(RTLD_NEXT, "readlink");
    if (real_fn == NULL) {
        errno = ENOSYS;
        return -1;
    }
    return real_fn(path, buf, size);
}

EXPORT ssize_t readlinkat(int dirfd, const char *path, char *buf, size_t size) {
    if (is_self_exe(path)) {
        ssize_t copied = copy_real_exe(buf, size);
        if (copied >= 0) return copied;
    }
    static readlinkat_fn real_fn = NULL;
    if (real_fn == NULL) real_fn = (readlinkat_fn) dlsym(RTLD_NEXT, "readlinkat");
    if (real_fn == NULL) {
        errno = ENOSYS;
        return -1;
    }
    return real_fn(dirfd, path, buf, size);
}
