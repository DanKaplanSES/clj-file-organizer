(ns fileorganizer.core
  (:require [seesaw.core :refer :all])
  (:require [seesaw.chooser :refer :all])
  (:require [seesaw.keymap :refer [map-key]])
  (:require [me.raynes.fs :refer [rename delete base-name]])
  (:require [fileorganizer.properties :refer [load-props]])
  (:import [javax.swing JFileChooser KeyStroke JComponent UIManager])
  (:import [java.awt.event KeyEvent])    
  (:import [java.awt Desktop Component])
  (:import [java.io File])
  )

(def filechooser-props (load-props "filechooser.properties"))

(native!)

(def shortcut-destination-map (atom {"Delete" "\"Reserved\""
                                     "Ctrl Z" "\"Reserved\""}))

(def last-shortcut-keystroke (atom nil))  ;todo: this may not need an atom

(def actions (atom []))

(defn clear-input-map [input-map key-stroke]
  (when input-map   
    (.remove input-map key-stroke)
    (clear-input-map (.getParent input-map) key-stroke)))

(defn for-each-component [f root]
  (when root
    (f root)
    (dorun 
      (map #(for-each-component f %)  (.getComponents root)))))

(defn clear-input-map-of [component key-stroke]      
  (for-each-component (fn [c] 
                        (when (instance? javax.swing.JComponent c)
                          (clear-input-map (.getInputMap c JComponent/WHEN_ANCESTOR_OF_FOCUSED_COMPONENT) key-stroke)
                          (clear-input-map (.getInputMap c JComponent/WHEN_IN_FOCUSED_WINDOW) key-stroke)
                          (clear-input-map (.getInputMap c JComponent/WHEN_FOCUSED) key-stroke))) component))


(defn file-chooser [current-dir] 
  (UIManager/put "FileChooser.readOnly" true)
  (doto (JFileChooser. current-dir)    
    (.. getActionMap (get "viewTypeDetails") (actionPerformed nil))))

(def main-frame (frame :title "File Organizer"))

(defn open-file [f]
  (.open (Desktop/getDesktop) f))

(defn my-file-filter [f] 
  (let [input (.getAbsolutePath f)
        sources (reduce conj #{} (map #(.getAbsolutePath (nth % 1)) @actions))]
    (not (sources input))))

(def fc (doto (file-chooser (File. (:main.dir filechooser-props)))                   
          (.setAcceptAllFileFilterUsed false)
          (config! :filters [(file-filter "My Files" my-file-filter)])          
          (.setControlButtonsAreShown false)
          (.setMultiSelectionEnabled true)            
          (listen :action (fn [e] (when (= (.getActionCommand e) "ApproveSelection") 
                                    (open-file (.getSelectedFile fc)))))))

(defn refresh-fc [] 
  (doto fc 
    (.rescanCurrentDirectory)
    (.setSelectedFile nil)
    (.setSelectedFiles nil)))

(def open-button (button :text "Open"))

(defn open-action [& e] 
  (when-let [f (.getSelectedFile fc)]
    (when (.isFile f)
      (open-file (.getSelectedFile fc)))))

(listen open-button :action open-action)

(def delete-button (button :text "Delete"))

(defn delete-file-action []
  (when-let [selected-file (.getSelectedFile fc)]
    (swap! actions conj [:delete selected-file])
    (refresh-fc)))    

(listen delete-button :action (fn [e] (delete-file-action)))

(defn undo-action []
  (when-not (empty? @actions) 
    (swap! actions pop)
    (refresh-fc)))    

(def undo-button (button :text "Undo"))

(listen undo-button :action (fn [e] (undo-action)))

(defn shortcut-listener [text e]
  (let [modifier (KeyEvent/getKeyModifiersText (.getModifiers e))        
        code (.getKeyCode e)
        key-text (KeyEvent/getKeyText code)
        modifier-pressed (some identity (map #(= code %) [KeyEvent/VK_CONTROL KeyEvent/VK_ALT KeyEvent/VK_SHIFT]))]
    (when-not modifier-pressed
      (reset! last-shortcut-keystroke (KeyStroke/getKeyStrokeForEvent e))
      (config! text :text (.trim (str modifier " " key-text))))))
    
(defn left-align [c]
  (doto c (.setAlignmentX Component/LEFT_ALIGNMENT)))

(defn make-shortcut-text []
  (text 
    :editable? false 
    :listen [:key-pressed #(shortcut-listener (.getSource %) %)]))

(map-key main-frame "DELETE" (fn [e] (delete-file-action)) :scope :global)
(map-key main-frame "ctrl Z" (fn [e] (undo-action)) :scope :global)
(map-key main-frame "ENTER" (fn [e] (open-action)) :scope :global)

(defn move-file-action [destination]
  {:pre [destination]}
  (when-let [selected-file (.getSelectedFile fc)]
    (swap! actions conj [:move selected-file destination])
    (refresh-fc)))    

(defn shortcut-ok-button-action [dialog fc previous-shortcut-destination-map shortcut-label shortcut-text destination-button error-label]  
  (let [selected-file (.getSelectedFile fc)        
        shortcut-string (config shortcut-text :text)]    
    (cond
      (not selected-file) (config! error-label :text "You must choose a directory")
      (empty? shortcut-string) (config! error-label :text "You must choose a shortcut")
      (get previous-shortcut-destination-map shortcut-string) (config! error-label :text (str "This shortcut is already bound to " (get previous-shortcut-destination-map shortcut-string)))
      :else (do              
              (reset! shortcut-destination-map (assoc previous-shortcut-destination-map shortcut-string (.getAbsolutePath selected-file)))              
              (config! shortcut-label :text shortcut-string)
              (config! destination-button :text (.getAbsolutePath selected-file))              
              (dispose! dialog)
              (map-key main-frame @last-shortcut-keystroke (fn [e] (move-file-action selected-file)) :scope :global)
              (clear-input-map-of fc @last-shortcut-keystroke)
              (reset! last-shortcut-keystroke nil)))))

(defn destination-shortcut-dialog [destination-button shortcut-label]
  (let [d (custom-dialog :title "Choose a Shortcut" :modal? true :parent main-frame)
        fc (doto (file-chooser (File. (:shortcut.dir filechooser-props)))                         
             (.setControlButtonsAreShown false) 
             (config! :selection-mode :dirs-only))                    
        st (make-shortcut-text)
        shortcut-panel (horizontal-panel :items ["Shortcut:" st]) 
        error-label (label :foreground :red)
        shortcut-text (if (= (config shortcut-label :text) "<Unset>") nil (config shortcut-label :text))
        frame-buttons (horizontal-panel :items [(button :text "OK"
                                                        :listen [:action (fn [e] (shortcut-ok-button-action d fc (dissoc @shortcut-destination-map shortcut-text) shortcut-label st destination-button error-label))]) 
                                                (button :text "Cancel"
                                                        :listen [:action (fn [e] (dispose! d))])])
        left-aligned-components (map left-align (list fc shortcut-panel frame-buttons error-label))]
    
    (config! d :content (vertical-panel :items left-aligned-components))
    (-> d pack! show!)))	

(defn destination [shortcut-label] 
  (let [destination-button (button :text "<Unset>")]                                          
    (listen destination-button :action (fn [e] (destination-shortcut-dialog destination-button shortcut-label)))
    destination-button))                                       

(def num-of-shortcuts 15)

(def shortcut-items (map (fn [_] (label "<Unset>")) (range num-of-shortcuts)))

(def shortcuts (grid-panel :columns 1 :items (conj shortcut-items "Shortcuts")))

(def destination-items (map (fn [x] (destination (nth shortcut-items x))) (range num-of-shortcuts)))

(def destinations (grid-panel :columns 1 :items (conj destination-items "Destinations")))

(def destination-shortcuts (horizontal-panel :items 
                                             [(left-right-split 
                                                destinations 
                                                shortcuts 
                                                :divider-location 2/3)]))

(defmulti action-as-string (fn [action] (nth action 0)))

(defmethod action-as-string :delete [[_ f]]
  (str "Deleting: " (.getAbsolutePath f)))

(defmethod action-as-string :move [[_ f d]]
  (str "Moving: " (.getAbsolutePath f) " to " (.getAbsolutePath d)))

(defn actions-as-string [actions]
  (reduce #(str %1 "\n" %2) (map action-as-string actions)))

(defmulti run-action (fn [action] (nth action 0)))

(defmethod run-action :delete [[_ f]]  
  (if-not (delete f)
    (alert "Could not delete file " f)))

(defmethod run-action :move [[_ f d]]    
  (if-not (rename f (File. d (base-name f)))
    (alert "Could not move " f " to " d)))

(defn commit-changes-action [e]  
  (dorun (map run-action @actions))
  (reset! actions []))

(defn show-changes-action [e]
  (-> (dialog :content (if-let [as (not-empty @actions)] (actions-as-string as) "NO CHANGES!") 
              :option-type :ok-cancel 
              :success-fn commit-changes-action) 
    pack! 
    show!))

(config! main-frame :content (vertical-panel :items [fc                                            
                                            (horizontal-panel :items [open-button delete-button])
                                            undo-button
                                            (separator)                                            
                                            destination-shortcuts
                                            (separator)
                                            (button :text "Commit changes" :listen [:action show-changes-action])]))

(-> main-frame pack! show!)
