"""인구를 노출량(offset)으로 둔 포아송 회귀 (순수 파이썬, 릿지 IRLS)."""
import math


def solve(a, b):
    """가우스 소거로 a x = b 를 푼다."""
    n = len(a)
    m = [row[:] + [b[i]] for i, row in enumerate(a)]
    for c in range(n):
        p = max(range(c, n), key=lambda r: abs(m[r][c]))
        m[c], m[p] = m[p], m[c]
        piv = m[c][c]
        if abs(piv) < 1e-12:
            continue
        for r in range(n):
            if r != c:
                f = m[r][c] / piv
                if f:
                    for k in range(c, n + 1):
                        m[r][k] -= f * m[c][k]
    return [m[i][n] / m[i][i] if abs(m[i][i]) > 1e-12 else 0.0 for i in range(n)]


def fit(xs, ys, offs, ridge=1.0, iters=30):
    """xs: 표준화된 특징(절편 제외), ys: 가게 수, offs: log(인구). 반환: 계수(절편 먼저)."""
    p = len(xs[0]) + 1
    total_y = sum(ys)
    total_e = sum(math.exp(o) for o in offs)
    beta = [math.log((total_y + 0.5) / total_e)] + [0.0] * (p - 1)
    for _ in range(iters):
        xtwx = [[0.0] * p for _ in range(p)]
        xtwz = [0.0] * p
        for x, y, o in zip(xs, ys, offs):
            row = [1.0] + x
            eta_lin = sum(bi * xi for bi, xi in zip(beta, row))
            mu = math.exp(max(-30.0, min(30.0, o + eta_lin)))
            w = max(mu, 1e-9)
            z = eta_lin + (y - mu) / w
            for i in range(p):
                wi = w * row[i]
                xtwz[i] += wi * z
                for j in range(i, p):
                    xtwx[i][j] += wi * row[j]
        for i in range(p):
            for j in range(i):
                xtwx[i][j] = xtwx[j][i]
            if i > 0:
                xtwx[i][i] += ridge
        new = solve(xtwx, xtwz)
        if max(abs(a - b) for a, b in zip(new, beta)) < 1e-7:
            beta = new
            break
        beta = new
    return beta


def predict(beta, x, off):
    eta = beta[0] + sum(b * v for b, v in zip(beta[1:], x))
    return math.exp(max(-30.0, min(30.0, off + eta)))


def deviance(y, mu):
    mu = max(mu, 1e-12)
    return 2 * ((y * math.log(y / mu) if y > 0 else 0.0) - (y - mu))


def poisson_cdf(k, mu):
    """P(Y <= k), Y ~ Poisson(mu)"""
    if mu <= 0:
        return 1.0
    term = math.exp(-mu)
    total = term
    for i in range(1, int(k) + 1):
        term *= mu / i
        total += term
    return min(1.0, total)
