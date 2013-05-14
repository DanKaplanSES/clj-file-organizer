(ns fileorganizer.document-filter
  (:import [javax.swing.text DocumentFilter])
  )

(def listen-doc-filter (DocumentFilter.))

(def ignore-doc-filter 
  (proxy [DocumentFilter] []
    (remove [fb offset length])
    (insertString [fb offset string attr])
    (replace [fb offset length text attrs])
    ))

(defn set-document-filter [text document-filter]
  (-> text 
    (.getDocument) 
    (.setDocumentFilter document-filter)))
