export function mountChart(canvas, caption, points, format, {
  labels = ["花费", "收入"], colors = ["#9fb4ca", "#2aa98c"], fields = ["spend", "revenue"]
} = {}) {
  const ctx = canvas.getContext("2d");
  if (!ctx || !points.length) return () => {};
  const max = Math.ceil(Math.max(1, ...points.flatMap(point => fields.map(field => point[field]))) / 10) * 10;
  let selection = -1;
  let bounds;
  function draw() {
    const ratio = window.devicePixelRatio || 1;
    const width = canvas.clientWidth;
    const height = canvas.clientHeight;
    if (!width || !height) return;
    canvas.width = width * ratio;
    canvas.height = height * ratio;
    ctx.scale(ratio, ratio);
    ctx.font = "12px system-ui";
    const left = Math.max(45, Math.ceil(ctx.measureText(max.toLocaleString("zh-CN")).width) + 12);
    const top = 13, bottom = height - 27, right = width - 9;
    const group = (right - left) / points.length;
    bounds = { left, group };
    ctx.textBaseline = "middle";
    for (let step = 0; step <= 4; step++) {
      const y = bottom - (bottom - top) * step / 4;
      ctx.fillStyle = "#62778c";
      ctx.textAlign = "right";
      ctx.fillText(Math.round(max * step / 4).toLocaleString("zh-CN"), left - 8, y);
      ctx.strokeStyle = "#e2eaf1";
      ctx.beginPath();
      ctx.moveTo(left, y);
      ctx.lineTo(right, y);
      ctx.stroke();
    }
    const bar = Math.min(22, group * .29);
    const labelsEvery = Math.max(1, Math.ceil(points.length / Math.max(2, Math.floor(width / 64))));
    points.forEach((point, index) => {
      const x = left + group * (index + .5);
      if (index === selection) {
        ctx.fillStyle = "#e9f6f2";
        ctx.fillRect(x - group / 2, top, group, bottom - top);
      }
      fields.forEach((field, series) => {
        const value = point[field];
        const offset = x + (series - (fields.length - 1) / 2) * (bar + 4) - bar / 2;
        const h = value / max * (bottom - top);
        ctx.fillStyle = colors[series];
        ctx.fillRect(offset, value >= 0 ? bottom - h : bottom, bar, Math.max(0, h));
      });
      if (index % labelsEvery === 0) {
        ctx.fillStyle = "#62778c";
        ctx.textAlign = "center";
        ctx.fillText(point.date.slice(5), x, bottom + 15);
      }
    });
  }
  function select(index) {
    if (selection === index) return;
    selection = index;
    const point = points[index];
    caption.textContent = point ? [point.date, ...fields.map((field, series) => `${labels[series]} ${format(point[field])}`)].join(" · ") : "悬停或按方向键查看每日数据";
    draw();
  }
  function pointer(event) {
    if (!bounds) return;
    select(Math.max(0, Math.min(points.length - 1, Math.floor((event.offsetX - bounds.left) / bounds.group))));
  }
  function keyboard(event) {
    if (!["ArrowLeft", "ArrowRight"].includes(event.key)) return;
    event.preventDefault();
    select(Math.max(0, Math.min(points.length - 1, selection + (event.key === "ArrowRight" ? 1 : -1))));
  }
  const observer = new ResizeObserver(draw);
  observer.observe(canvas);
  canvas.addEventListener("pointermove", pointer);
  canvas.addEventListener("pointerleave", () => select(-1));
  canvas.addEventListener("keydown", keyboard);
  draw();
  return () => observer.disconnect();
}

