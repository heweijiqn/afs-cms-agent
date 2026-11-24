import React, { useEffect } from "react";
import { closeDialog, syncModalState } from "../modules/ui";

export const ModalHost: React.FC = () => {
  useEffect(() => {
    const modal = document.getElementById("modal") as HTMLDialogElement | null;
    if (!modal) return;
    // Esc / 浏览器原生关闭时同步背景滚动锁，并释放上一次的弹窗内容。
    const onClose = () => {
      syncModalState();
      const content = document.getElementById("modal-content");
      content?.replaceChildren();
      const title = document.getElementById("modal-title");
      if (title) title.textContent = "";
    };
    modal.addEventListener("close", onClose);
    return () => modal.removeEventListener("close", onClose);
  }, []);

  return (
    <>
      <dialog id="modal" aria-labelledby="modal-title">
        <div className="modal-body">
          <header className="modal-header">
            <h2 id="modal-title" />
            <button
              className="icon-button"
              aria-label="关闭"
              type="button"
              id="close-modal"
              onClick={() => closeDialog()}
            >
              ×
            </button>
          </header>
          <div id="modal-content" />
        </div>
      </dialog>
      <output id="toast" className="toast" aria-live="polite" hidden />
    </>
  );
};
