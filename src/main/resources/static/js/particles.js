/* particles.js — 手写 canvas 彩色粒子背景（KGC 品牌视觉同款，零依赖）
   用法：页面放置 <canvas id="particleCanvas">，脚本自动初始化。
   特性：45% 彩色放射短线 + 55% 圆点；鼠标排斥（R=150 F=1.6）与回位弹簧（0.008）；
   正弦漂移让背景保持动态；DPR 适配 cap 2；resize 200ms 防抖；prefers-reduced-motion 只画一帧。
   延迟初始化：路由组件挂载后 canvas 才出现，500ms 轮询最多 10s。
   物理参数与坑位详见 KGC skill references/particles-glassmorphism-visual.md */
(function () {
    var canvas = null, ctx = null, particles = [], W = 0, H = 0, raf = null, running = false;
    var mouse = { x: -9999, y: -9999 };
    var COLORS = ['#4285f4', '#ea4335', '#fbbc05', '#34a853', '#1a73e8', '#7c4dff', '#00bcd4'];

    function seed() {
        var n = Math.floor(W * H / 7500);
        n = Math.max(30, Math.min(n, 90));
        particles = [];
        var cx = W / 2, cy = H / 2;
        for (var i = 0; i < n; i++) {
            var x = Math.random() * W, y = Math.random() * H;
            particles.push({
                x: x, y: y, hx: x, hy: y,
                r: Math.random() * 2 + 0.8,
                line: Math.random() < 0.45,
                ang: Math.atan2(y - cy, x - cx) + (Math.random() - 0.5) * 0.6,
                len: Math.random() * 10 + 6,
                color: COLORS[Math.floor(Math.random() * COLORS.length)],
                alpha: 0.12 + Math.random() * 0.18,
                ph: Math.random() * Math.PI * 2
            });
        }
    }

    function resize() {
        var r = canvas.getBoundingClientRect();
        // 兜底：canvas 绝对定位拉伸失效（headless/部分渲染模式）时取父容器尺寸
        if ((r.width < 10 || r.width === 300) && canvas.parentElement) {
            var pr = canvas.parentElement.getBoundingClientRect();
            if (pr.width > 10) r = pr;
        }
        W = r.width || window.innerWidth;
        H = r.height || window.innerHeight;
        var dpr = Math.min(window.devicePixelRatio || 1, 2);
        canvas.width = W * dpr;
        canvas.height = H * dpr;
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        mouse.x = -9999; mouse.y = -9999;
        seed();
    }

    function step() {
        ctx.clearRect(0, 0, W, H);
        var t = Date.now();
        for (var i = 0; i < particles.length; i++) {
            var p = particles[i];
            var dx = p.x - mouse.x, dy = p.y - mouse.y;
            var d2 = dx * dx + dy * dy, R2 = 150 * 150;
            if (d2 < R2 && d2 > 0.01) {
                var d = Math.sqrt(d2), f = (1 - d / 150) * 1.6;
                p.x += dx / d * f; p.y += dy / d * f;
            }
            p.x += (p.hx - p.x) * 0.008;
            p.y += (p.hy - p.y) * 0.008;
            p.hx += Math.cos(t * 0.0003 + p.ph) * 0.1;
            p.hy += Math.sin(t * 0.0002 + p.ph) * 0.1;
            ctx.globalAlpha = p.alpha;
            ctx.strokeStyle = p.color;
            ctx.fillStyle = p.color;
            if (p.line) {
                var a = p.ang + Math.sin(t * 0.0002 + p.ph) * 0.15;
                ctx.lineWidth = 1.2;
                ctx.beginPath();
                ctx.moveTo(p.x, p.y);
                ctx.lineTo(p.x + Math.cos(a) * p.len, p.y + Math.sin(a) * p.len);
                ctx.stroke();
            } else {
                ctx.beginPath();
                ctx.arc(p.x, p.y, p.r, 0, Math.PI * 2);
                ctx.fill();
            }
        }
        ctx.globalAlpha = 1;
        raf = requestAnimationFrame(step);
    }

    function init() {
        canvas = document.getElementById('particleCanvas');
        if (!canvas) {
            var tries = 0;
            var timer = setInterval(function () {
                tries++;
                canvas = document.getElementById('particleCanvas');
                if (canvas || tries >= 20) { clearInterval(timer); if (canvas) init(); }
            }, 500);
            return;
        }
        if (running) return;
        running = true;
        ctx = canvas.getContext('2d');
        var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
        resize();
        var to = null;
        window.addEventListener('resize', function () { clearTimeout(to); to = setTimeout(resize, 200); });
        canvas.addEventListener('mousemove', function (e) {
            var r = canvas.getBoundingClientRect();
            mouse.x = e.clientX - r.left; mouse.y = e.clientY - r.top;
        });
        canvas.addEventListener('mouseleave', function () { mouse.x = -9999; mouse.y = -9999; });
        if (reduced) { step(); cancelAnimationFrame(raf); } else { step(); }
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
