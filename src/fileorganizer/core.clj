(ns fileorganizer.core
  (:require [seesaw.core :refer :all])
  (:require [seesaw.chooser :refer :all])
  (:require [seesaw.keymap :refer [map-key]])
  (:import [javax.swing JFileChooser KeyStroke])
  (:import [java.awt.event KeyEvent])    
  (:import [java.awt Desktop Component])   
  )

(native!)

(def shortcut-destination-map (atom {"Delete" "Reserved"}))

(def last-shortcut-keystroke (atom nil))

(defn file-chooser [] (JFileChooser.))    

(defn open-file [f]
  (.open (Desktop/getDesktop) f))

(def fc (doto (file-chooser)
          (.setControlButtonsAreShown false)
          (.setMultiSelectionEnabled true)          
          (listen :action (fn [e] (when (= (.getActionCommand e) "ApproveSelection") 
                                    (open-file (.getSelectedFile fc)))))
          ))

(defn delete-file-action []
  (when-let [selected-file (.getSelectedFile fc)]
    (println "I would delete " selected-file)))

(def open-button (button :text "Open"))
(listen open-button :action (fn [e] (when-let [f (.getSelectedFile fc)]
                                      (when (.isFile f)
                                        (open-file (.getSelectedFile fc))))))
(def delete-button (button :text "Delete"))
(listen delete-button :action (fn [e] (delete-file-action)))

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

(def f (frame :title "File Organizer"))

(map-key f "DELETE" (fn [e] (delete-file-action)) :scope :global)

(defn move-file-action [destination]
  {:pre [destination]}
  (when-let [selected-file (.getSelectedFile fc)]
    (println "I would move " selected-file " to " destination )))

(defn shortcut-ok-button-action [dialog fc previous-shortcut-destination-map shortcut-label shortcut-text destination-button error-label]
  {:pre [@last-shortcut-keystroke]}
  (let [selected-file (.getSelectedFile fc)        
        shortcut-string (config shortcut-text :text)]
    (cond
      (not selected-file) (config! error-label :text "You must choose a directory")
      (empty? shortcut-string) (config! error-label :text "You must choose a shortcut")
      (get previous-shortcut-destination-map shortcut-string) (config! error-label :text (str "This shortcut is already bound to " (get previous-shortcut-destination-map shortcut-string)))
      :else (do              
              (swap! shortcut-destination-map assoc shortcut-string (.getAbsolutePath selected-file))  
              (config! shortcut-label :text shortcut-string)
              (config! destination-button :text (.getAbsolutePath selected-file))
              (dispose! dialog)
              (map-key f @last-shortcut-keystroke (fn [e] (move-file-action selected-file)) :scope :global)
              (reset! last-shortcut-keystroke nil)))))

(defn destination-shortcut-dialog [destination-button shortcut-label]
  (let [d (custom-dialog :title "Choose a Shortcut" :modal? true :parent f)
        fc (doto (file-chooser) (.setControlButtonsAreShown false) (config! :selection-mode :dirs-only))
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

(def num-of-shortcuts 7)

(def shortcut-items (map (fn [_] (label "<Unset>")) (range num-of-shortcuts)))

(def shortcuts (grid-panel :columns 1 :items (conj shortcut-items "Shortcuts")))

(def destination-items (map (fn [x] (destination (nth shortcut-items x))) (range num-of-shortcuts)))

(def destinations (grid-panel :columns 1 :items (conj destination-items "Destinations")))

(def destination-shortcuts (horizontal-panel :items 
                                             [(left-right-split 
                                                destinations 
                                                shortcuts 
                                                :divider-location 2/3)]))

(config! f :content (vertical-panel :items [fc                                            
                                            (horizontal-panel :items [open-button delete-button])
                                            (separator)                                            
                                            destination-shortcuts]))

(-> f pack! show!)


; (-> (frame :content (vertical-panel :items [(file-chooser) (label :text "Hi")])) pack! show!)
