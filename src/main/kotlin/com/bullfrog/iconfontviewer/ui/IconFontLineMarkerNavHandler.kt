package com.bullfrog.iconfontviewer.ui

import com.bullfrog.iconfontviewer.model.IconFontPopupModel
import com.bullfrog.iconfontviewer.util.*
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiReferenceExpression
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.xml.XmlAttribute
import com.intellij.psi.xml.XmlTag
import com.intellij.ui.CollectionListModel
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.speedSearch.NameFilteringListModel
import com.intellij.ui.speedSearch.SpeedSearch
import com.intellij.util.ui.JBEmptyBorder
import com.intellij.util.ui.JBUI
import org.jetbrains.kotlin.psi.KtPsiFactory
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.event.DocumentEvent


class IconFontLineMarkerNavHandler(
    private val smartPointer: SmartPsiElementPointer<PsiElement>,
    private val matchedFont: java.awt.Font,
    private val fontPath: String
) : GutterIconNavigationHandler<PsiElement> {

    override fun navigate(e: MouseEvent?, elt: PsiElement?) {
        val element = smartPointer.element ?: return
        val speedSearch = SpeedSearch().apply {
            setEnabled(true)
        }

        val iconList = buildIconListForFont(matchedFont, fontPath, element)
        if (iconList.isEmpty()) return

        val collectionModel = CollectionListModel(iconList)
        val filteringModel = NameFilteringListModel<IconFontPopupModel>(
            collectionModel,
            { it.key },
            speedSearch::shouldBeShowing,
            { StringUtil.notNullize(speedSearch.filter) }
        )

        speedSearch.addChangeListener {
            filteringModel.refilter()
        }

        var popup: JBPopup? = null
        val (panel, searchField) = buildPopupPanel(filteringModel, { popup?.cancel() }, speedSearch)

        popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(panel, searchField)
            .setFocusable(true)
            .setRequestFocus(true)
            .createPopup()
        popup.showInScreenCoordinates(
            e?.component ?: return,
            MouseInfo.getPointerInfo().location
        )
    }

    private fun buildPopupPanel(
        listModel: NameFilteringListModel<IconFontPopupModel>,
        closeAction: () -> Unit,
        speedSearch: SpeedSearch
    ): Pair<JPanel, SearchTextField> {
        val searchField = SearchTextField().apply {
            isFocusable = true
            border = BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, JBUI.CurrentTheme.Popup.separatorColor()),
                JBEmptyBorder(8, 8, 8, 8)
            )
            addDocumentListener(object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) {
                    speedSearch.updatePattern(e.document.getText(0, e.document.length))
                }
            })
        }

        val panel = JPanel().apply {
            preferredSize = Dimension(300.jbScale(), 450.jbScale())
            layout = BorderLayout()
            add(searchField, BorderLayout.NORTH)
            add(
                JScrollPane().apply {
                    preferredSize = Dimension(300.jbScale(), 400.jbScale())
                    viewport.view = JList<IconFontPopupModel>().apply {
                        border = JBUI.Borders.empty(4.jbScale(), 8.jbScale())
                        model = listModel
                        cellRenderer = PopupListCellRenderer()
                        addMouseListener(object : MouseAdapter() {
                            override fun mouseClicked(e: MouseEvent) {
                                val idx = locationToIndex(e.point)
                                if (idx < 0) return
                                val cellBounds = getCellBounds(idx, idx) ?: return
                                if (!cellBounds.contains(e.point)) return
                                val selected = listModel.getElementAt(idx) ?: return
                                doReplace(selected)
                                closeAction()
                            }
                        })
                    }
                    border = BorderFactory.createEmptyBorder()
                },
                BorderLayout.CENTER
            )
            isVisible = true
        }

        return panel to searchField
    }

    class PopupListCellRenderer : ListCellRenderer<IconFontPopupModel> {
        override fun getListCellRendererComponent(
            list: JList<out IconFontPopupModel>?,
            value: IconFontPopupModel?,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            return JPanel().apply {
                border = JBUI.Borders.empty(10.jbScale())
                layout = BoxLayout(this, BoxLayout.X_AXIS)
                background = if (isSelected) list?.selectionBackground else list?.background
                add(JLabel().apply {
                    icon = value?.icon
                })
                add(JLabel().apply {
                    border = JBUI.Borders.emptyLeft(8.jbScale())
                    text = value?.key ?: ""
                })
            }
        }
    }

    private fun doReplace(model: IconFontPopupModel) {
        val project = smartPointer.project
        ApplicationManager.getApplication().invokeLater {
            WriteCommandAction.runWriteCommandAction(
                project,
                "Replace IconFont",
                null,
                {
                    val element = smartPointer.element ?: return@runWriteCommandAction
                    when {
                        element.isValidExpression() -> {
                            val replacement = if (element is PsiReferenceExpression) {
                                PsiElementFactory.getInstance(project)
                                    .createExpressionFromText(R_PREFIX + model.key, element)
                            } else {
                                KtPsiFactory(project).createExpression(R_PREFIX + model.key)
                            }
                            element.replace(replacement)
                        }
                        element.isValidLayoutXmlElement() -> {
                            val parent = PsiTreeUtil.getParentOfType(element, XmlAttribute::class.java)
                            parent?.setValue(XML_PREFIX + model.key)
                        }
                        element.isStringResourceTagName() -> {
                            val xmlTag = element.getParentStringResourceTag() ?: return@runWriteCommandAction
                            xmlTag.value.setText(model.text)
                        }
                    }
                }
            )
        }
    }
}
