import React from "react";
import { closeDialog } from "../modules/ui";

export const ModalHost: React.FC = () => {
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
