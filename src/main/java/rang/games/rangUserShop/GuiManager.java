package rang.games.rangUserShop;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.SignGUIAction;
import de.rapha149.signgui.exception.SignGUIVersionException;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import rang.games.rangGiftBox.api.GiftBoxAPI;
import rang.games.rangUserShop.data.*;
import rang.games.rangUserShop.event.*;
import rang.games.rangUserShop.util.ItemHashUtil;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static rang.games.rangUserShop.RangUserShop.SortOrder;
import static rang.games.rangUserShop.RangUserShop.ManagementTab;
import static rang.games.rangUserShop.RangUserShop.MainGuiTab;

public class GuiManager {

    public static final String GUI_PREFIX = ChatColor.DARK_AQUA + "[유저상점] ";

    public static final int GUI_ROW_SIZE = 9;
    public static final int GUI_FULL_SIZE = 54;
    public static final int GUI_ITEM_SLOTS_START = 0;
    public static final int GUI_ITEM_SLOTS_END = 44;
    public static final int GUI_NAV_START_SLOT = 45;
    public static final int GUI_NAV_END_SLOT = 53;

    public static final int MAIN_TAB_SHOP_SLOT = 45;
    public static final int MAIN_TAB_AUCTION_SLOT = 46;
    public static final int MAIN_TAB_BUY_REQUEST_SLOT = 47;
    public static final int MAIN_PREV_PAGE_SLOT = 48;
    public static final int MAIN_PAGE_INFO_SLOT = 49;
    public static final int MAIN_NEXT_PAGE_SLOT = 50;
    public static final int MAIN_REFRESH_SLOT = 51;
    public static final int MAIN_SEARCH_HELP_SLOT = 52;
    public static final int MAIN_SORT_SLOT = 53;


    public static final int PURCHASE_CONFIRM_GUI_SIZE = 9;
    public static final int PURCHASE_DISPLAY_ITEM_SLOT = 4;
    public static final int PLAYER_BALANCE_SLOT = 1;
    public static final int CONFIRM_PURCHASE_SLOT = 3;
    public static final int CANCEL_PURCHASE_SLOT = 5;
    public static final int TOTAL_PRICE_SLOT = 7;

    public static final int AUCTION_BID_GUI_SIZE = 9;
    public static final int AUCTION_DISPLAY_ITEM_SLOT = 4;
    public static final int AUCTION_CURRENT_BID_SLOT = 1;
    public static final int AUCTION_YOUR_BALANCE_SLOT = 2;
    public static final int AUCTION_PLACE_BID_SLOT = 5;
    public static final int AUCTION_BUY_NOW_SLOT = 6;
    public static final int AUCTION_CANCEL_SLOT = 8;

    public static final int BUY_REQUEST_FULFILL_GUI_SIZE = 9;
    public static final int BUY_REQUEST_DISPLAY_ITEM_SLOT = 4;
    public static final int BUY_REQUEST_INFO_SLOT = 1;
    public static final int BUY_REQUEST_FULFILL_ALL_SLOT = 3;
    public static final int BUY_REQUEST_FULFILL_PARTIAL_SLOT = 5;
    public static final int BUY_REQUEST_CANCEL_SLOT = 8;

    public static final int MANAGEMENT_TAB_SELLING_SLOT = 48;
    public static final int MANAGEMENT_TAB_SOLD_EXPIRED_SLOT = 49;
    public static final int MANAGEMENT_TAB_STORAGE_INFO_SLOT = 50;
    public static final int MANAGEMENT_BACK_TO_MAIN_SLOT = 53;
    public static final int MANAGEMENT_PREV_PAGE_SLOT = 45;
    public static final int MANAGEMENT_PAGE_INFO_SLOT = 46;
    public static final int MANAGEMENT_NEXT_PAGE_SLOT = 47;


    private final RangUserShop plugin;
    private final DatabaseManager dbManager;
    private final EconomyManager economyManager;
    private final GiftBoxAPI giftBoxAPI;
    private final DecimalFormat formatter = new DecimalFormat("#,###");
    private final NamespacedKey shopItemIdKey;
    private final NamespacedKey auctionItemIdKey;
    private final NamespacedKey buyRequestIdKey;
    private final NamespacedKey shopSellerUuidKey;

    private final Map<UUID, ManagementTab> playerCurrentManagementTab = new HashMap<>();

    public GuiManager(RangUserShop plugin) {
        this.plugin = plugin;
        this.dbManager = plugin.getDatabaseManager();
        this.economyManager = plugin.getEconomyManager();
        this.giftBoxAPI = plugin.getGiftBoxAPI();
        this.shopItemIdKey = new NamespacedKey(plugin, "shop_item_id");
        this.auctionItemIdKey = new NamespacedKey(plugin, "auction_item_id");
        this.buyRequestIdKey = new NamespacedKey(plugin, "buy_request_id");
        this.shopSellerUuidKey = new NamespacedKey(plugin, "shop_seller_uuid");
    }

    public ManagementTab getPlayerCurrentManagementTab(UUID uuid) {
        return playerCurrentManagementTab.getOrDefault(uuid, ManagementTab.SELLING);
    }

    public void openMainShop(Player player, MainGuiTab tab, int page) {
        plugin.setPlayerCurrentMainTab(player.getUniqueId(), tab);
        plugin.setPlayerCurrentPage(player.getUniqueId(), page);
        String searchTerm = plugin.getPlayerSearchTerm(player.getUniqueId());
        UUID filterSellerUuid = plugin.getPlayerFilterSellerUuid(player.getUniqueId());

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<?> itemsToDisplay;
            String guiTitle = GUI_PREFIX;
            int totalPages;

            switch (tab) {
                case SHOP:
                    List<ShopItem> shopItems = dbManager.getAllListedShopItems();
                    if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                        String lowerCaseSearchTerm = searchTerm.trim().toLowerCase();
                        shopItems = shopItems.stream()
                                .filter(item -> item.getItemStack().getType().name().toLowerCase().contains(lowerCaseSearchTerm) ||
                                        (item.getItemStack().hasItemMeta() && item.getItemStack().getItemMeta().hasDisplayName() && ChatColor.stripColor(item.getItemStack().getItemMeta().getDisplayName()).toLowerCase().contains(lowerCaseSearchTerm)) ||
                                        item.getSellerName().toLowerCase().contains(lowerCaseSearchTerm))
                                .collect(Collectors.toList());
                    }
                    if (filterSellerUuid != null) {
                        shopItems = shopItems.stream()
                                .filter(item -> item.getSellerUuid().equals(filterSellerUuid))
                                .collect(Collectors.toList());
                    }
                    SortOrder sortOrder = plugin.getCurrentSortOrder();
                    shopItems.sort(getShopItemComparator(sortOrder));
                    itemsToDisplay = shopItems;
                    guiTitle += "상점";
                    break;
                case AUCTION:
                    List<AuctionItem> auctionItems = dbManager.getAllActiveAuctionItems();
                    if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                        String lowerCaseSearchTerm = searchTerm.trim().toLowerCase();
                        auctionItems = auctionItems.stream()
                                .filter(item -> item.getItemStack().getType().name().toLowerCase().contains(lowerCaseSearchTerm) ||
                                        (item.getItemStack().hasItemMeta() && item.getItemStack().getItemMeta().hasDisplayName() && ChatColor.stripColor(item.getItemStack().getItemMeta().getDisplayName()).toLowerCase().contains(lowerCaseSearchTerm)) ||
                                        item.getSellerName().toLowerCase().contains(lowerCaseSearchTerm))
                                .collect(Collectors.toList());
                    }
                    if (filterSellerUuid != null) {
                        auctionItems = auctionItems.stream()
                                .filter(item -> item.getSellerUuid().equals(filterSellerUuid))
                                .collect(Collectors.toList());
                    }
                    auctionItems.sort(Comparator.comparing(AuctionItem::getEndTimestamp));
                    itemsToDisplay = auctionItems;
                    guiTitle += "경매장";
                    break;
                case BUY_REQUESTS:
                    List<BuyRequest> buyRequests = dbManager.getAllActiveBuyRequests();
                    if (searchTerm != null && !searchTerm.trim().isEmpty()) {
                        String lowerCaseSearchTerm = searchTerm.trim().toLowerCase();
                        buyRequests = buyRequests.stream()
                                .filter(item -> item.getItemStack().getType().name().toLowerCase().contains(lowerCaseSearchTerm) ||
                                        (item.getItemStack().hasItemMeta() && item.getItemStack().getItemMeta().hasDisplayName() && ChatColor.stripColor(item.getItemStack().getItemMeta().getDisplayName()).toLowerCase().contains(lowerCaseSearchTerm)) ||
                                        item.getRequesterName().toLowerCase().contains(lowerCaseSearchTerm))
                                .collect(Collectors.toList());
                    }
                    if (filterSellerUuid != null) {
                        buyRequests = buyRequests.stream()
                                .filter(item -> item.getRequesterUuid().equals(filterSellerUuid))
                                .collect(Collectors.toList());
                    }
                    buyRequests.sort(Comparator.comparing(BuyRequest::getPricePerItem).reversed());
                    itemsToDisplay = buyRequests;
                    guiTitle += "구매 요청";
                    break;
                default:
                    return;
            }

            totalPages = (int) Math.ceil(itemsToDisplay.size() / (double) (GUI_ITEM_SLOTS_END + 1));
            if (totalPages == 0) totalPages = 1;

            int finalPage = Math.max(1, Math.min(page, totalPages));
            plugin.setPlayerCurrentPage(player.getUniqueId(), finalPage);

            String finalGuiTitle = guiTitle + " (" + finalPage + "/" + totalPages + ")";
            if (searchTerm != null && !searchTerm.isEmpty()) {
                finalGuiTitle += " - 검색: " + searchTerm;
            }
            if (filterSellerUuid != null) {
                OfflinePlayer filteredPlayer = Bukkit.getOfflinePlayer(filterSellerUuid);
                if (filteredPlayer.hasPlayedBefore()) {
                    finalGuiTitle += " - 판매자: " + filteredPlayer.getName();
                }
            }

            Inventory gui = Bukkit.createInventory(null, GUI_FULL_SIZE, finalGuiTitle);

            int startIndex = (finalPage - 1) * (GUI_ITEM_SLOTS_END + 1);
            for (int i = 0; i <= GUI_ITEM_SLOTS_END; i++) {
                int itemIndex = startIndex + i;
                if (itemIndex < itemsToDisplay.size()) {
                    Object item = itemsToDisplay.get(itemIndex);
                    if (item instanceof ShopItem) {
                        gui.setItem(i, createDisplayItem((ShopItem) item));
                    } else if (item instanceof AuctionItem) {
                        gui.setItem(i, createDisplayItem((AuctionItem) item));
                    } else if (item instanceof BuyRequest) {
                        gui.setItem(i, createDisplayItem((BuyRequest) item));
                    }
                }
            }

            addMainNavigationBar(player, gui, tab, finalPage, totalPages);
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(gui));
        });
    }

    public void openManagementGui(Player player, ManagementTab tab, int page) {
        playerCurrentManagementTab.put(player.getUniqueId(), tab);
        plugin.setPlayerCurrentPage(player.getUniqueId(), page);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            String guiTitle = GUI_PREFIX + "물품 관리 - " + tab.getDisplayName();
            List<ItemStack> combinedItems = new ArrayList<>();

            if (tab == ManagementTab.SELLING || tab == ManagementTab.SOLD_EXPIRED) {
                List<ShopItem> shopItemsToDisplay = new ArrayList<>();
                List<AuctionItem> auctionItemsToDisplay = new ArrayList<>();

                if (tab == ManagementTab.SELLING) {
                    shopItemsToDisplay = dbManager.getShopItemsBySeller(player.getUniqueId());
                    auctionItemsToDisplay = dbManager.getAuctionItemsBySeller(player.getUniqueId()).stream()
                            .filter(a -> a.getStatus().equals("ACTIVE")).collect(Collectors.toList());
                    shopItemsToDisplay.sort(Comparator.comparing(ShopItem::getExpiryTimestamp));
                    auctionItemsToDisplay.sort(Comparator.comparing(AuctionItem::getEndTimestamp));
                } else {
                    shopItemsToDisplay.addAll(dbManager.getShopItemsBySellerAndStatus(player.getUniqueId(), "SOLD"));
                    shopItemsToDisplay.addAll(dbManager.getShopItemsBySellerAndStatus(player.getUniqueId(), "EXPIRED"));
                    shopItemsToDisplay.sort(Comparator.comparing(ShopItem::getExpiryTimestamp).reversed());
                    auctionItemsToDisplay.addAll(dbManager.getAuctionItemsBySeller(player.getUniqueId()).stream()
                            .filter(a -> a.getStatus().equals("ENDED") || a.getStatus().equals("CANCELLED"))
                            .collect(Collectors.toList()));
                    auctionItemsToDisplay.sort(Comparator.comparing(AuctionItem::getEndTimestamp).reversed());
                }

                for (ShopItem item : shopItemsToDisplay) {
                    combinedItems.add(createManagementDisplayItem(item, tab));
                }
                for (AuctionItem item : auctionItemsToDisplay) {
                    combinedItems.add(createManagementDisplayItem(item, tab));
                }
            }

            int totalPages = (int) Math.ceil(combinedItems.size() / (double) (GUI_ITEM_SLOTS_END + 1));
            if (totalPages == 0) totalPages = 1;

            int finalPage = Math.max(1, Math.min(page, totalPages));
            plugin.setPlayerCurrentPage(player.getUniqueId(), finalPage);

            Inventory gui = Bukkit.createInventory(null, GUI_FULL_SIZE, guiTitle + " (" + finalPage + "/" + totalPages + ")");

            if (tab == ManagementTab.STORAGE_INFO) {
                ItemStack infoBook = createNavItem(Material.BOOK, ChatColor.YELLOW + "보관함 정책 안내",
                        ChatColor.GRAY + "판매 완료/만료된 아이템은 30일간 보관됩니다.",
                        ChatColor.GRAY + "이후에는 자동으로 삭제됩니다.",
                        ChatColor.GRAY + "판매 완료/만료 탭에서 아이템을 회수할 수 있습니다.");
                gui.setItem(22, infoBook);
            } else {
                int startIndex = (finalPage - 1) * (GUI_ITEM_SLOTS_END + 1);
                for (int i = 0; i <= GUI_ITEM_SLOTS_END; i++) {
                    int itemIndex = startIndex + i;
                    if (itemIndex < combinedItems.size()) {
                        gui.setItem(i, combinedItems.get(itemIndex));
                    }
                }
            }

            addManagementNavigationBar(gui, tab, finalPage, totalPages);
            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(gui));
        });
    }

    public void openPurchaseConfirmGui(Player player, ShopItem item) {
        Inventory gui = Bukkit.createInventory(null, PURCHASE_CONFIRM_GUI_SIZE, GUI_PREFIX + "구매 확인");

        ItemStack displayItem = item.getItemStack().clone();
        displayItem.setAmount(item.getAmount());
        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (meta.hasLore()) {
                lore.addAll(meta.getLore());
                lore.add(" ");
            }
            lore.add(ChatColor.AQUA + "판매자: " + ChatColor.WHITE + item.getSellerName());
            lore.add(ChatColor.AQUA + "개당 가격: " + ChatColor.WHITE + formatter.format(item.getPrice()) + "원");
            lore.add(ChatColor.AQUA + "총 수량: " + ChatColor.WHITE + item.getAmount() + "개");
            lore.add(ChatColor.AQUA + "총 가격: " + ChatColor.WHITE + formatter.format(item.getPrice() * item.getAmount()) + "원");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(shopItemIdKey, PersistentDataType.INTEGER, item.getId());
            displayItem.setItemMeta(meta);
        }
        gui.setItem(PURCHASE_DISPLAY_ITEM_SLOT, displayItem);

        double playerBalance = economyManager.getBalance(player);
        gui.setItem(PLAYER_BALANCE_SLOT, createNavItem(Material.GOLD_INGOT, ChatColor.YELLOW + "내 잔액", ChatColor.WHITE + formatter.format(playerBalance) + "원"));

        ItemStack confirmButton = createNavItem(Material.LIME_WOOL, ChatColor.GREEN + "구매 확정", ChatColor.GRAY + "클릭하여 아이템을 구매합니다.");
        gui.setItem(CONFIRM_PURCHASE_SLOT, confirmButton);

        gui.setItem(CANCEL_PURCHASE_SLOT, createNavItem(Material.RED_WOOL, ChatColor.RED + "취소", ChatColor.GRAY + "클릭하여 구매를 취소합니다."));

        double totalPrice = item.getPrice() * item.getAmount();
        ItemStack totalPriceItem = createNavItem(Material.EMERALD, ChatColor.GREEN + "총 구매 가격", ChatColor.WHITE + formatter.format(totalPrice) + "원");
        if (playerBalance < totalPrice) {
            ItemMeta totalMeta = totalPriceItem.getItemMeta();
            if (totalMeta != null) {
                List<String> totalLore = totalMeta.hasLore() ? new ArrayList<>(totalMeta.getLore()) : new ArrayList<>();
                totalLore.add(ChatColor.RED + "잔액이 부족합니다!");
                totalMeta.setLore(totalLore);
                totalPriceItem.setItemMeta(totalMeta);
            }
            ItemMeta confirmMeta = confirmButton.getItemMeta();
            if (confirmMeta != null) {
                confirmButton.setType(Material.GRAY_WOOL);
                confirmMeta.setDisplayName(ChatColor.GRAY + "잔액 부족");
                confirmButton.setItemMeta(confirmMeta);
            }
        }
        gui.setItem(TOTAL_PRICE_SLOT, totalPriceItem);

        player.openInventory(gui);
    }

    public void openAuctionBidGui(Player player, AuctionItem item) {
        Inventory gui = Bukkit.createInventory(null, AUCTION_BID_GUI_SIZE, GUI_PREFIX + "경매 입찰");

        ItemStack displayItem = item.getItemStack().clone();
        displayItem.setAmount(1);
        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (meta.hasLore()) {
                lore.addAll(meta.getLore());
                lore.add(" ");
            }
            lore.add(ChatColor.AQUA + "판매자: " + ChatColor.WHITE + item.getSellerName());
            lore.add(ChatColor.AQUA + "시작가: " + ChatColor.WHITE + formatter.format(item.getStartPrice()) + "원");
            lore.add(ChatColor.AQUA + "남은 시간: " + ChatColor.WHITE + formatDuration(item.getEndTimestamp() - System.currentTimeMillis()));
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(auctionItemIdKey, PersistentDataType.INTEGER, item.getId());
            displayItem.setItemMeta(meta);
        }
        gui.setItem(AUCTION_DISPLAY_ITEM_SLOT, displayItem);

        gui.setItem(AUCTION_CURRENT_BID_SLOT, createNavItem(Material.GOLD_NUGGET, ChatColor.YELLOW + "현재 최고 입찰가", ChatColor.WHITE + formatter.format(item.getCurrentBid()) + "원", (item.getHighestBidderName() != null ? ChatColor.GRAY + "입찰자: " + item.getHighestBidderName() : "")));
        gui.setItem(AUCTION_YOUR_BALANCE_SLOT, createNavItem(Material.GOLD_INGOT, ChatColor.YELLOW + "내 잔액", ChatColor.WHITE + formatter.format(economyManager.getBalance(player)) + "원"));

        gui.setItem(AUCTION_PLACE_BID_SLOT, createNavItem(Material.LIME_WOOL, ChatColor.GREEN + "입찰하기", ChatColor.GRAY + "클릭하여 입찰가를 입력합니다."));

        if (item.getBuyNowPrice() > 0) {
            gui.setItem(AUCTION_BUY_NOW_SLOT, createNavItem(Material.GOLD_BLOCK, ChatColor.YELLOW + "즉시 구매하기", ChatColor.WHITE + formatter.format(item.getBuyNowPrice()) + "원", ChatColor.GRAY + "클릭하여 즉시 구매합니다."));
        } else {
            gui.setItem(AUCTION_BUY_NOW_SLOT, createNavItem(Material.BARRIER, ChatColor.RED + "즉시 구매 불가", ""));
        }

        gui.setItem(AUCTION_CANCEL_SLOT, createNavItem(Material.RED_WOOL, ChatColor.RED + "취소", ChatColor.GRAY + "클릭하여 돌아갑니다."));

        player.openInventory(gui);
    }

    public void openBuyRequestFulfillGui(Player player, BuyRequest request) {
        Inventory gui = Bukkit.createInventory(null, BUY_REQUEST_FULFILL_GUI_SIZE, GUI_PREFIX + "구매 요청 이행");

        ItemStack displayItem = request.getItemStack().clone();
        displayItem.setAmount(request.getAmountRequested() - request.getAmountFulfilled());
        ItemMeta meta = displayItem.getItemMeta();
        if (meta != null) {
            List<String> lore = new ArrayList<>();
            if (meta.hasLore()) {
                lore.addAll(meta.getLore());
                lore.add(" ");
            }
            lore.add(ChatColor.AQUA + "요청자: " + ChatColor.WHITE + request.getRequesterName());
            lore.add(ChatColor.AQUA + "개당 가격: " + ChatColor.WHITE + formatter.format(request.getPricePerItem()) + "원");
            lore.add(ChatColor.AQUA + "요청 수량: " + ChatColor.WHITE + request.getAmountRequested() + "개");
            lore.add(ChatColor.AQUA + "남은 수량: " + ChatColor.WHITE + (request.getAmountRequested() - request.getAmountFulfilled()) + "개");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(buyRequestIdKey, PersistentDataType.INTEGER, request.getId());
            displayItem.setItemMeta(meta);
        }
        gui.setItem(BUY_REQUEST_DISPLAY_ITEM_SLOT, displayItem);

        gui.setItem(BUY_REQUEST_INFO_SLOT, createNavItem(Material.PAPER, ChatColor.YELLOW + "판매 정보",
                ChatColor.AQUA + "내 인벤토리에서 아이템이 차감됩니다.",
                ChatColor.AQUA + "즉시 대금이 지급됩니다."));

        gui.setItem(BUY_REQUEST_FULFILL_ALL_SLOT, createNavItem(Material.LIME_WOOL, ChatColor.GREEN + "모두 판매하기",
                ChatColor.GRAY + "남은 수량(" + (request.getAmountRequested() - request.getAmountFulfilled()) + "개)을 모두 판매합니다."));

        gui.setItem(BUY_REQUEST_FULFILL_PARTIAL_SLOT, createNavItem(Material.YELLOW_WOOL, ChatColor.YELLOW + "일부 판매하기",
                ChatColor.GRAY + "클릭하여 판매할 수량을 입력합니다."));

        gui.setItem(BUY_REQUEST_CANCEL_SLOT, createNavItem(Material.RED_WOOL, ChatColor.RED + "취소", ChatColor.GRAY + "클릭하여 돌아갑니다."));

        player.openInventory(gui);
    }

    public void openLowestPriceListGui(Player player, ItemStack itemStack) {
        String itemHash = ItemHashUtil.generateItemHashForComparison(itemStack);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<ShopItem> items = dbManager.getListedItemsByHash(itemHash);
            int size = (int) (Math.ceil(items.size() / 9.0) * 9);
            if (size == 0) {
                player.sendMessage(ChatColor.YELLOW + itemStack.getType().name() + "의 판매 중인 매물을 찾을 수 없습니다.");
                return;
            }
            size = Math.min(size, 54);

            String title = GUI_PREFIX + "최저가 목록 - " + (itemStack.hasItemMeta() && itemStack.getItemMeta().hasDisplayName() ? itemStack.getItemMeta().getDisplayName() : itemStack.getType().name());
            Inventory gui = Bukkit.createInventory(null, size, title);

            for (int i = 0; i < items.size() && i < 54; i++) {
                gui.setItem(i, createDisplayItem(items.get(i)));
            }

            Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(gui));
        });
    }

    public void openSearchGui(Player player) {
        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", "검색어를 입력하세요", "입력 후 표지판 닫기")
                    .setHandler((p, result) -> {
                        String searchTerm = result.getLine(0).trim();
                        plugin.setPlayerSearchTerm(p.getUniqueId(), searchTerm.isEmpty() ? null : searchTerm);
                        p.sendMessage(ChatColor.GREEN + (searchTerm.isEmpty() ? "검색이 초기화되었습니다." : "'" + searchTerm + "'(으)로 상점을 검색합니다."));
                        return List.of(SignGUIAction.run(() -> openMainShop(p, plugin.getPlayerCurrentMainTab(p.getUniqueId()), 1)));
                    })
                    .build()
                    .open(player);
        } catch (SignGUIVersionException e) {
            plugin.getLogger().log(Level.SEVERE, "현재 서버 버전에서는 SignGUI를 지원하지 않습니다.", e);
            player.sendMessage(ChatColor.RED + "오류: 검색 기능을 현재 사용할 수 없습니다. 관리자에게 문의하세요.");
        }
    }

    public void openBidSignGui(Player player, ItemStack auctionItemDisplay) {
        int auctionId = extractAuctionItemId(auctionItemDisplay);
        if (auctionId == -1) return;
        AuctionItem auctionItem = dbManager.getAuctionItemById(auctionId);
        if (auctionItem == null) return;

        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", "입찰가를 입력하세요", "현재: " + formatter.format(auctionItem.getCurrentBid()))
                    .setHandler((p, result) -> {
                        String bidString = result.getLine(0).trim();
                        try {
                            double bidAmount = Double.parseDouble(bidString);
                            handlePlaceBid(p, auctionItem.getId(), bidAmount);
                        } catch (NumberFormatException e) {
                            p.sendMessage(ChatColor.RED + "올바른 숫자를 입력해주세요.");
                            return List.of(SignGUIAction.run(() -> openAuctionBidGui(p, auctionItem)));
                        }
                        return Collections.emptyList();
                    })
                    .build()
                    .open(player);
        } catch (SignGUIVersionException e) {
            plugin.getLogger().log(Level.SEVERE, "현재 서버 버전에서는 SignGUI를 지원하지 않습니다.", e);
            player.sendMessage(ChatColor.RED + "오류: 입찰 기능을 현재 사용할 수 없습니다. 관리자에게 문의하세요.");
        }
    }

    public void openFulfillPartialSignGui(Player player, ItemStack buyRequestDisplay) {
        int requestId = extractBuyRequestId(buyRequestDisplay);
        if (requestId == -1) return;
        BuyRequest buyRequest = dbManager.getBuyRequestById(requestId);
        if (buyRequest == null) return;

        try {
            SignGUI.builder()
                    .setLines("", "^^^^^^^^^^^^^^^", "판매할 수량을 입력하세요", "남은 수량: " + (buyRequest.getAmountRequested() - buyRequest.getAmountFulfilled()))
                    .setHandler((p, result) -> {
                        String amountString = result.getLine(0).trim();
                        try {
                            int amountToSell = Integer.parseInt(amountString);
                            handleFulfillBuyRequest(p, buyRequest.getId(), amountToSell);
                        } catch (NumberFormatException e) {
                            p.sendMessage(ChatColor.RED + "올바른 숫자를 입력해주세요.");
                            return List.of(SignGUIAction.run(() -> openBuyRequestFulfillGui(p, buyRequest)));
                        }
                        return Collections.emptyList();
                    })
                    .build()
                    .open(player);
        } catch (SignGUIVersionException e) {
            plugin.getLogger().log(Level.SEVERE, "현재 서버 버전에서는 SignGUI를 지원하지 않습니다.", e);
            player.sendMessage(ChatColor.RED + "오류: 판매 기능을 현재 사용할 수 없습니다. 관리자에게 문의하세요.");
        }
    }

    public void handleMainShopItemClick(Player player, ItemStack clickedItem, ClickType clickType) {
        MainGuiTab currentTab = plugin.getPlayerCurrentMainTab(player.getUniqueId());

        switch (currentTab) {
            case SHOP:
                int shopItemId = extractShopItemId(clickedItem);
                if (shopItemId == -1) return;
                ShopItem shopItem = dbManager.getShopItemById(shopItemId);
                if (shopItem == null || !shopItem.getStatus().equals("LISTED") || shopItem.getExpiryTimestamp() <= System.currentTimeMillis()) {
                    player.sendMessage(ChatColor.RED + "만료되었거나 이미 판매된 아이템입니다.");
                    openMainShop(player, currentTab, plugin.getPlayerCurrentPage(player.getUniqueId()));
                    return;
                }
                if (clickType.isLeftClick()) {
                    openPurchaseConfirmGui(player, shopItem);
                } else if (clickType.isRightClick()) {
                    openLowestPriceListGui(player, shopItem.getItemStack());
                } else if (clickType.isShiftClick() && clickType.isRightClick()) {
                    UUID sellerUuid = extractSellerUuid(clickedItem);
                    if (sellerUuid != null) {
                        plugin.setPlayerFilterSellerUuid(player.getUniqueId(), sellerUuid);
                        OfflinePlayer filteredPlayer = Bukkit.getOfflinePlayer(sellerUuid);
                        player.sendMessage(ChatColor.GREEN + (filteredPlayer.hasPlayedBefore() ? filteredPlayer.getName() : "알 수 없는 판매자") + "님의 상점만 표시합니다. 필터를 초기화하려면 '검색 / 도움말' 버튼을 Shift+좌클릭하세요.");
                        openMainShop(player, currentTab, 1);
                    } else {
                        player.sendMessage(ChatColor.RED + "판매자 정보를 찾을 수 없습니다.");
                    }
                } else if (clickType.isShiftClick()) {
                    handleDetailedPriceInfo(player, shopItem.getItemStack());
                }
                break;
            case AUCTION:
                int auctionItemId = extractAuctionItemId(clickedItem);
                if (auctionItemId == -1) return;
                AuctionItem auctionItem = dbManager.getAuctionItemById(auctionItemId);
                if (auctionItem == null || !auctionItem.getStatus().equals("ACTIVE") || auctionItem.getEndTimestamp() <= System.currentTimeMillis()) {
                    player.sendMessage(ChatColor.RED + "만료되었거나 이미 종료된 경매입니다.");
                    openMainShop(player, currentTab, plugin.getPlayerCurrentPage(player.getUniqueId()));
                    return;
                }
                if (clickType.isLeftClick()) {
                    openAuctionBidGui(player, auctionItem);
                } else if (clickType.isRightClick() && auctionItem.getBuyNowPrice() > 0) {
                    handleAuctionBuyNow(player, clickedItem);
                } else if (clickType.isShiftClick() && clickType.isRightClick()) {
                    UUID sellerUuid = extractSellerUuid(clickedItem);
                    if (sellerUuid != null) {
                        plugin.setPlayerFilterSellerUuid(player.getUniqueId(), sellerUuid);
                        OfflinePlayer filteredPlayer = Bukkit.getOfflinePlayer(sellerUuid);
                        player.sendMessage(ChatColor.GREEN + (filteredPlayer.hasPlayedBefore() ? filteredPlayer.getName() : "알 수 없는 판매자") + "님의 경매만 표시합니다. 필터를 초기화하려면 '검색 / 도움말' 버튼을 Shift+좌클릭하세요.");
                        openMainShop(player, currentTab, 1);
                    } else {
                        player.sendMessage(ChatColor.RED + "판매자 정보를 찾을 수 없습니다.");
                    }
                }
                break;
            case BUY_REQUESTS:
                int buyRequestId = extractBuyRequestId(clickedItem);
                if (buyRequestId == -1) return;
                BuyRequest buyRequest = dbManager.getBuyRequestById(buyRequestId);
                if (buyRequest == null || !buyRequest.getStatus().equals("ACTIVE") || buyRequest.getAmountFulfilled() >= buyRequest.getAmountRequested()) {
                    player.sendMessage(ChatColor.RED + "만료되었거나 이미 완료된 구매 요청입니다.");
                    openMainShop(player, currentTab, plugin.getPlayerCurrentPage(player.getUniqueId()));
                    return;
                }
                if (clickType.isLeftClick()) {
                    openBuyRequestFulfillGui(player, buyRequest);
                } else if (clickType.isShiftClick() && clickType.isRightClick()) {
                    UUID requesterUuid = extractSellerUuid(clickedItem);
                    if (requesterUuid != null) {
                        plugin.setPlayerFilterSellerUuid(player.getUniqueId(), requesterUuid);
                        OfflinePlayer filteredPlayer = Bukkit.getOfflinePlayer(requesterUuid);
                        player.sendMessage(ChatColor.GREEN + (filteredPlayer.hasPlayedBefore() ? filteredPlayer.getName() : "알 수 없는 요청자") + "님의 구매 요청만 표시합니다. 필터를 초기화하려면 '검색 / 도움말' 버튼을 Shift+좌클릭하세요.");
                        openMainShop(player, currentTab, 1);
                    } else {
                        player.sendMessage(ChatColor.RED + "요청자 정보를 찾을 수 없습니다.");
                    }
                }
                break;
        }
    }

    public void handleManagementItemClick(Player player, ItemStack clickedItem, ClickType clickType) {
        ManagementTab currentTab = getPlayerCurrentManagementTab(player.getUniqueId());

        int shopItemId = extractShopItemId(clickedItem);
        int auctionItemId = extractAuctionItemId(clickedItem);

        if (shopItemId != -1) {
            ShopItem shopItem = dbManager.getShopItemById(shopItemId);
            if (shopItem == null || !shopItem.getSellerUuid().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "해당 아이템을 관리할 권한이 없습니다.");
                player.closeInventory();
                return;
            }
            if (currentTab == ManagementTab.SELLING) {
                if (clickType.isLeftClick()) {
                    handleCancelSale(player, shopItem);
                }
            } else if (currentTab == ManagementTab.SOLD_EXPIRED) {
                if (clickType.isLeftClick()) {
                    handleReclaimItem(player, shopItem);
                }
            }
        } else if (auctionItemId != -1) {
            AuctionItem auctionItem = dbManager.getAuctionItemById(auctionItemId);
            if (auctionItem == null || !auctionItem.getSellerUuid().equals(player.getUniqueId())) {
                player.sendMessage(ChatColor.RED + "해당 경매를 관리할 권한이 없습니다.");
                player.closeInventory();
                return;
            }
            if (currentTab == ManagementTab.SELLING) {
                if (clickType.isLeftClick()) {
                    handleCancelAuction(player, auctionItem);
                }
            } else if (currentTab == ManagementTab.SOLD_EXPIRED) {
                if (clickType.isLeftClick()) {
                    handleReclaimAuctionItem(player, auctionItem);
                }
            }
        }
    }

    public void handlePurchase(Player buyer, ItemStack displayItem) {
        int itemId = extractShopItemId(displayItem);
        if (itemId == -1) {
            buyer.sendMessage(ChatColor.RED + "구매 정보를 찾을 수 없습니다.");
            buyer.closeInventory();
            return;
        }

        ShopItem shopItem = dbManager.getShopItemById(itemId);
        if (shopItem == null || !shopItem.getStatus().equals("LISTED") || shopItem.getExpiryTimestamp() <= System.currentTimeMillis()) {
            buyer.sendMessage(ChatColor.RED + "만료되었거나 이미 판매된 아이템입니다.");
            buyer.closeInventory();
            return;
        }

        double totalPrice = shopItem.getPrice() * shopItem.getAmount();

        if (economyManager.getBalance(buyer) < totalPrice) {
            buyer.sendMessage(ChatColor.RED + "잔액이 부족하여 아이템을 구매할 수 없습니다!");
            buyer.closeInventory();
            return;
        }

        double finalSellerAmount = totalPrice;
        boolean applyTax = !Bukkit.getOfflinePlayer(shopItem.getSellerUuid()).getPlayer().hasPermission("usershop.tax.exempt");

        if (applyTax) {
            finalSellerAmount *= 0.95;
            buyer.sendMessage(ChatColor.YELLOW + "판매 수수료 5%가 적용됩니다.");
        }

        if (!economyManager.withdrawPlayer(buyer, totalPrice)) {
            buyer.sendMessage(ChatColor.RED + "돈을 인출하는 데 실패했습니다. 다시 시도해주세요.");
            buyer.closeInventory();
            return;
        }

        if (!economyManager.depositPlayer(shopItem.getSellerUuid(), finalSellerAmount)) {
            buyer.sendMessage(ChatColor.RED + "판매자에게 돈을 입금하는 데 실패했습니다. 관리자에게 문의하세요.");
            economyManager.depositPlayer(buyer.getUniqueId(), totalPrice);
            buyer.closeInventory();
            return;
        }

        ItemStack purchasedItem = shopItem.getItemStack().clone();
        purchasedItem.setAmount(shopItem.getAmount());

        giveItemToPlayer(buyer, purchasedItem, "상점 구매 아이템");

        if (dbManager.updateShopItemStatus(shopItem.getId(), "SOLD")) {
            dbManager.saveTransaction(new Transaction(0, shopItem.getId(), buyer.getUniqueId(), buyer.getName(), shopItem.getSellerUuid(), shopItem.getSellerName(), shopItem.getItemStack(), shopItem.getPrice(), shopItem.getAmount(), System.currentTimeMillis(), "SALE"));
            buyer.sendMessage(ChatColor.GREEN + shopItem.getSellerName() + "님의 " + purchasedItem.getType().name() + " " + shopItem.getAmount() + "개를 성공적으로 구매했습니다!");
            Player seller = Bukkit.getPlayer(shopItem.getSellerUuid());
            if (seller != null && seller.isOnline()) {
                seller.sendMessage(ChatColor.GREEN + buyer.getName() + "님이 당신의 " + purchasedItem.getType().name() + " " + shopItem.getAmount() + "개를 " + formatter.format(finalSellerAmount) + "원에 구매했습니다!");
            }
            Bukkit.getPluginManager().callEvent(new ShopItemPurchasedEvent(shopItem, buyer, shopItem.getAmount(), totalPrice));
        } else {
            buyer.sendMessage(ChatColor.RED + "아이템 구매 후 데이터베이스 업데이트에 실패했습니다. 관리자에게 문의하세요.");
        }

        buyer.closeInventory();
        openMainShop(buyer, plugin.getPlayerCurrentMainTab(buyer.getUniqueId()), plugin.getPlayerCurrentPage(buyer.getUniqueId()));
    }

    public void handleAuctionBuyNow(Player buyer, ItemStack displayItem) {
        int auctionId = extractAuctionItemId(displayItem);
        if (auctionId == -1) {
            buyer.sendMessage(ChatColor.RED + "경매 정보를 찾을 수 없습니다.");
            buyer.closeInventory();
            return;
        }
        AuctionItem auctionItem = dbManager.getAuctionItemById(auctionId);
        if (auctionItem == null || !auctionItem.getStatus().equals("ACTIVE") || auctionItem.getEndTimestamp() <= System.currentTimeMillis()) {
            buyer.sendMessage(ChatColor.RED + "만료되었거나 이미 종료된 경매입니다.");
            buyer.closeInventory();
            return;
        }

        double buyNowPrice = auctionItem.getBuyNowPrice();

        if (buyNowPrice <= 0) {
            buyer.sendMessage(ChatColor.RED + "이 경매는 즉시 구매가 불가능합니다.");
            buyer.closeInventory();
            return;
        }

        if (economyManager.getBalance(buyer) < buyNowPrice) {
            buyer.sendMessage(ChatColor.RED + "잔액이 부족하여 즉시 구매할 수 없습니다!");
            buyer.closeInventory();
            return;
        }

        double finalSellerAmount = buyNowPrice;
        boolean applyTax = !Bukkit.getOfflinePlayer(auctionItem.getSellerUuid()).getPlayer().hasPermission("usershop.tax.exempt");

        if (applyTax) {
            finalSellerAmount *= 0.95;
            buyer.sendMessage(ChatColor.YELLOW + "판매 수수료 5%가 적용됩니다.");
        }

        if (!economyManager.withdrawPlayer(buyer, buyNowPrice)) {
            buyer.sendMessage(ChatColor.RED + "돈을 인출하는 데 실패했습니다. 다시 시도해주세요.");
            buyer.closeInventory();
            return;
        }

        if (!economyManager.depositPlayer(auctionItem.getSellerUuid(), finalSellerAmount)) {
            buyer.sendMessage(ChatColor.RED + "판매자에게 돈을 입금하는 데 실패했습니다. 관리자에게 문의하세요.");
            economyManager.depositPlayer(buyer.getUniqueId(), buyNowPrice);
            buyer.closeInventory();
            return;
        }

        ItemStack purchasedItem = auctionItem.getItemStack().clone();
        purchasedItem.setAmount(1);

        giveItemToPlayer(buyer, purchasedItem, "경매 즉시 구매 아이템");

        if (dbManager.updateAuctionItemStatus(auctionItem.getId(), "ENDED")) {
            dbManager.saveTransaction(new Transaction(0, auctionItem.getId(), buyer.getUniqueId(), buyer.getName(), auctionItem.getSellerUuid(), auctionItem.getSellerName(), auctionItem.getItemStack(), buyNowPrice, 1, System.currentTimeMillis(), "AUCTION_PURCHASE"));
            buyer.sendMessage(ChatColor.GREEN + auctionItem.getSellerName() + "님의 경매 아이템을 즉시 구매했습니다!");
            Player seller = Bukkit.getPlayer(auctionItem.getSellerUuid());
            if (seller != null && seller.isOnline()) {
                seller.sendMessage(ChatColor.GREEN + buyer.getName() + "님이 당신의 경매 아이템을 즉시 구매하여 " + formatter.format(finalSellerAmount) + "원에 판매되었습니다!");
            }
            Bukkit.getPluginManager().callEvent(new AuctionEndedEvent(auctionItem, AuctionEndedEvent.Reason.BUY_NOW, buyer));
        } else {
            buyer.sendMessage(ChatColor.RED + "즉시 구매 후 데이터베이스 업데이트에 실패했습니다. 관리자에게 문의하세요.");
        }

        buyer.closeInventory();
        openMainShop(buyer, MainGuiTab.AUCTION, plugin.getPlayerCurrentPage(buyer.getUniqueId()));
    }

    public void handleFulfillBuyRequest(Player seller, ItemStack displayItem, int amountToSell) {
        int requestId = extractBuyRequestId(displayItem);
        if (requestId == -1) {
            seller.sendMessage(ChatColor.RED + "구매 요청 정보를 찾을 수 없습니다.");
            seller.closeInventory();
            return;
        }
        handleFulfillBuyRequest(seller, requestId, amountToSell);
    }


    private void handleFulfillBuyRequest(Player seller, int buyRequestId, int amountToSell) {
        BuyRequest buyRequest = dbManager.getBuyRequestById(buyRequestId);
        if (buyRequest == null || !buyRequest.getStatus().equals("ACTIVE") || buyRequest.getAmountFulfilled() >= buyRequest.getAmountRequested()) {
            seller.sendMessage(ChatColor.RED + "만료되었거나 이미 완료된 구매 요청입니다.");
            seller.closeInventory();
            return;
        }

        int remainingToFulfill = buyRequest.getAmountRequested() - buyRequest.getAmountFulfilled();
        if (amountToSell == -1) {
            amountToSell = remainingToFulfill;
        }

        if (amountToSell <= 0) {
            seller.sendMessage(ChatColor.RED + "판매할 수량은 1개 이상이어야 합니다.");
            return;
        }

        if (amountToSell > remainingToFulfill) {
            seller.sendMessage(ChatColor.RED + "판매할 수량이 남은 요청 수량보다 많습니다.");
            return;
        }

        int availableInInventory = 0;
        for (ItemStack item : seller.getInventory().getContents()) {
            if (item != null && ItemHashUtil.generateItemHashForComparison(item).equals(buyRequest.getItemHash())) {
                availableInInventory += item.getAmount();
            }
        }

        if (availableInInventory < amountToSell) {
            seller.sendMessage(ChatColor.RED + "인벤토리에 판매할 아이템이 부족합니다. (" + amountToSell + "개 필요, " + availableInInventory + "개 보유)");
            seller.closeInventory();
            return;
        }


        double totalPrice = buyRequest.getPricePerItem() * amountToSell;

        if (!economyManager.depositPlayer(seller.getUniqueId(), totalPrice)) {
            seller.sendMessage(ChatColor.RED + "대금을 지급하는 데 실패했습니다. 관리자에게 문의하세요.");
            seller.closeInventory();
            return;
        }

        ItemStack toRemove = buyRequest.getItemStack().clone();
        toRemove.setAmount(amountToSell);
        seller.getInventory().removeItem(toRemove);
        seller.updateInventory();

        int newFulfilledAmount = buyRequest.getAmountFulfilled() + amountToSell;
        boolean fullyFulfilled = newFulfilledAmount >= buyRequest.getAmountRequested();

        if (dbManager.updateBuyRequestAmountFulfilled(buyRequest.getId(), newFulfilledAmount)) {
            if (fullyFulfilled) {
                dbManager.updateBuyRequestStatus(buyRequest.getId(), "FULFILLED");
            }
            dbManager.saveTransaction(new Transaction(0, buyRequest.getId(), buyRequest.getRequesterUuid(), buyRequest.getRequesterName(), seller.getUniqueId(), seller.getName(), buyRequest.getItemStack(), buyRequest.getPricePerItem(), amountToSell, System.currentTimeMillis(), "BUY_REQUEST_FULFILLMENT"));
            seller.sendMessage(ChatColor.GREEN + buyRequest.getRequesterName() + "님의 구매 요청에 " + amountToSell + "개의 " + buyRequest.getItemStack().getType().name() + "를 판매했습니다. 대금 " + formatter.format(totalPrice) + "원이 지급되었습니다.");
            OfflinePlayer requester = Bukkit.getOfflinePlayer(buyRequest.getRequesterUuid());
            if (requester.isOnline()) {
                requester.getPlayer().sendMessage(ChatColor.GREEN + seller.getName() + "님이 당신의 구매 요청에 " + amountToSell + "개의 " + buyRequest.getItemStack().getType().name() + "를 판매했습니다!");
            }
            Bukkit.getPluginManager().callEvent(new BuyRequestFulfilledEvent(buyRequest, seller, amountToSell, totalPrice));
        } else {
            seller.sendMessage(ChatColor.RED + "구매 요청 이행 후 데이터베이스 업데이트에 실패했습니다. 관리자에게 문의하세요.");
        }

        seller.closeInventory();
        openMainShop(seller, MainGuiTab.BUY_REQUESTS, plugin.getPlayerCurrentPage(seller.getUniqueId()));
    }

    private void handlePlaceBid(Player bidder, int auctionId, double bidAmount) {
        AuctionItem auctionItem = dbManager.getAuctionItemById(auctionId);
        if (auctionItem == null || !auctionItem.getStatus().equals("ACTIVE")) {
            bidder.sendMessage(ChatColor.RED + "이미 종료된 경매입니다.");
            return;
        }

        if (bidAmount <= auctionItem.getCurrentBid()) {
            bidder.sendMessage(ChatColor.RED + "입찰가는 현재 최고 입찰가보다 높아야 합니다.");
            return;
        }

        if (economyManager.getBalance(bidder) < bidAmount) {
            bidder.sendMessage(ChatColor.RED + "잔액이 부족합니다.");
            return;
        }

        if (!economyManager.withdrawPlayer(bidder, bidAmount)) {
            bidder.sendMessage(ChatColor.RED + "입찰 금액을 인출하는 데 실패했습니다.");
            return;
        }

        UUID previousBidderUuid = auctionItem.getHighestBidderUuid();
        double previousBidAmount = auctionItem.getCurrentBid();

        if (dbManager.updateAuctionItemBid(auctionId, bidAmount, bidder.getUniqueId(), bidder.getName())) {
            if (previousBidderUuid != null) {
                economyManager.depositPlayer(previousBidderUuid, previousBidAmount);
                OfflinePlayer prevBidder = Bukkit.getOfflinePlayer(previousBidderUuid);
                if (prevBidder.isOnline()) {
                    prevBidder.getPlayer().sendMessage(ChatColor.YELLOW + "당신의 입찰이 " + bidder.getName() + "에 의해 상회되어 " + formatter.format(previousBidAmount) + "원이 환불되었습니다.");
                }
            }

            dbManager.saveBid(new Bid(0, auctionId, bidder.getUniqueId(), bidder.getName(), bidAmount, System.currentTimeMillis()));
            bidder.sendMessage(ChatColor.GREEN + "성공적으로 " + formatter.format(bidAmount) + "원에 입찰했습니다!");
            Bukkit.getPluginManager().callEvent(new AuctionBidEvent(auctionItem, bidder, bidAmount));

            AuctionItem updatedItem = dbManager.getAuctionItemById(auctionId);
            if (updatedItem != null) {
                openAuctionBidGui(bidder, updatedItem);
            } else {
                bidder.closeInventory();
            }

        } else {
            bidder.sendMessage(ChatColor.RED + "입찰에 실패했습니다. 다른 유저가 먼저 입찰했을 수 있습니다.");
            economyManager.depositPlayer(bidder.getUniqueId(), bidAmount);
            openAuctionBidGui(bidder, auctionItem);
        }
    }

    private void handleCancelSale(Player player, ShopItem shopItem) {
        if (dbManager.updateShopItemStatus(shopItem.getId(), "CANCELLED")) {
            ItemStack cancelledItem = shopItem.getItemStack().clone();
            cancelledItem.setAmount(shopItem.getAmount());

            giveItemToPlayer(player, cancelledItem, "취소된 판매 아이템");
            player.sendMessage(ChatColor.GREEN + "판매를 취소했습니다.");

            Bukkit.getPluginManager().callEvent(new ShopItemCancelledEvent(shopItem, player));
            openManagementGui(player, ManagementTab.SELLING, 1);
        } else {
            player.sendMessage(ChatColor.RED + "아이템 판매 취소에 실패했습니다. 관리자에게 문의하세요.");
        }
    }

    private void handleCancelAuction(Player player, AuctionItem auctionItem) {
        if (dbManager.updateAuctionItemStatus(auctionItem.getId(), "CANCELLED")) {
            ItemStack cancelledItem = auctionItem.getItemStack().clone();
            cancelledItem.setAmount(1);

            giveItemToPlayer(player, cancelledItem, "취소된 경매 아이템");
            player.sendMessage(ChatColor.GREEN + "경매를 취소했습니다.");

            if (auctionItem.getHighestBidderUuid() != null) {
                economyManager.depositPlayer(auctionItem.getHighestBidderUuid(), auctionItem.getCurrentBid());
                OfflinePlayer prevBidder = Bukkit.getOfflinePlayer(auctionItem.getHighestBidderUuid());
                if (prevBidder.isOnline()) {
                    prevBidder.getPlayer().sendMessage(ChatColor.YELLOW + "입찰했던 경매가 취소되어 " + formatter.format(auctionItem.getCurrentBid()) + "원이 환불되었습니다.");
                }
            }
            Bukkit.getPluginManager().callEvent(new AuctionEndedEvent(auctionItem, AuctionEndedEvent.Reason.CANCELLED, player));
            openManagementGui(player, ManagementTab.SELLING, 1);
        } else {
            player.sendMessage(ChatColor.RED + "경매 취소에 실패했습니다. 관리자에게 문의하세요.");
        }
    }

    private void handleReclaimItem(Player player, ShopItem shopItem) {
        if (!shopItem.getSellerUuid().equals(player.getUniqueId()) ||
                !(shopItem.getStatus().equals("EXPIRED") || shopItem.getStatus().equals("CANCELLED"))) {
            player.sendMessage(ChatColor.RED + "회수할 수 없는 아이템입니다.");
            player.closeInventory();
            return;
        }

        ItemStack reclaimedItem = shopItem.getItemStack().clone();
        reclaimedItem.setAmount(shopItem.getAmount());

        if (dbManager.deleteShopItem(shopItem.getId())) {
            giveItemToPlayer(player, reclaimedItem, "회수된 아이템");
            Bukkit.getPluginManager().callEvent(new ShopItemReclaimedEvent(shopItem, player));
            openManagementGui(player, ManagementTab.SOLD_EXPIRED, 1);
        } else {
            player.sendMessage(ChatColor.RED + "아이템 회수 후 데이터베이스 업데이트에 실패했습니다. 관리자에게 문의하세요.");
        }
    }

    private void handleReclaimAuctionItem(Player player, AuctionItem auctionItem) {
        boolean canReclaim = false;
        String reason = "";
        ItemStack itemToGive = null;

        if (auctionItem.getStatus().equals("ENDED")) {
            if (auctionItem.getHighestBidderUuid() != null && auctionItem.getHighestBidderUuid().equals(player.getUniqueId())) {
                canReclaim = true;
                reason = "낙찰받은 경매 아이템";
                itemToGive = auctionItem.getItemStack().clone();
            } else if (auctionItem.getHighestBidderUuid() == null && auctionItem.getSellerUuid().equals(player.getUniqueId())) {
                canReclaim = true;
                reason = "유찰된 경매 아이템";
                itemToGive = auctionItem.getItemStack().clone();
            }
        } else if (auctionItem.getStatus().equals("CANCELLED")) {
            if (auctionItem.getSellerUuid().equals(player.getUniqueId())) {
                canReclaim = true;
                reason = "취소된 경매 아이템";
                itemToGive = auctionItem.getItemStack().clone();
            }
        }

        if (!canReclaim) {
            player.sendMessage(ChatColor.RED + "회수할 수 없는 경매 아이템입니다.");
            player.closeInventory();
            return;
        }

        if (dbManager.updateAuctionItemStatus(auctionItem.getId(), "RECLAIMED")) {
            giveItemToPlayer(player, itemToGive, reason);
            openManagementGui(player, ManagementTab.SOLD_EXPIRED, 1);
        } else {
            player.sendMessage(ChatColor.RED + "경매 아이템 회수 후 데이터베이스 업데이트에 실패했습니다. 관리자에게 문의하세요.");
        }
    }

    public void handleDetailedPriceInfo(Player player, ItemStack itemStack) {
        String itemHash = ItemHashUtil.generateItemHashForComparison(itemStack);

        long twentyFourHoursAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000L);

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<Transaction> transactions = dbManager.getTransactionsByItemHash(itemHash, twentyFourHoursAgo, System.currentTimeMillis());
            int totalListed = dbManager.getListedItemCount(itemHash);
            ShopItem lowestPriceItem = dbManager.getLowestPriceItem(itemHash);

            double totalVolume = 0;
            double totalAmount = 0;
            for (Transaction tx : transactions) {
                totalVolume += tx.getPricePerItem() * tx.getAmount();
                totalAmount += tx.getAmount();
            }
            double averagePrice = totalAmount > 0 ? totalVolume / totalAmount : 0;
            final double finalTotalAmount = totalAmount;

            Bukkit.getScheduler().runTask(plugin, () -> {
                player.closeInventory();
                player.sendMessage(ChatColor.GOLD + "--- [" + itemStack.getType().name() + "] 상세 시세 정보 ---");
                player.sendMessage(ChatColor.AQUA + "현재 최저가: " + ChatColor.WHITE + (lowestPriceItem != null ? formatter.format(lowestPriceItem.getPrice()) + "원" : "판매 중인 매물 없음"));
                player.sendMessage(ChatColor.AQUA + "24시간 평균가: " + ChatColor.WHITE + formatter.format(averagePrice) + "원");
                player.sendMessage(ChatColor.AQUA + "24시간 거래량: " + ChatColor.WHITE + finalTotalAmount + "개");
                player.sendMessage(ChatColor.AQUA + "총 등록 수량: " + ChatColor.WHITE + totalListed + "개");
                player.sendMessage(ChatColor.GOLD + "--------------------------");
            });
        });
    }

    public void displayHelp(Player player) {
        player.closeInventory();
        player.sendMessage(ChatColor.GOLD + "--- 유저상점 도움말 ---");
        player.sendMessage(ChatColor.YELLOW + "/상점" + ChatColor.GRAY + " - 상점을 엽니다.");
        player.sendMessage(ChatColor.YELLOW + "/상점 판매 <가격> [수량]" + ChatColor.GRAY + " - 손에 든 아이템을 판매합니다.");
        player.sendMessage(ChatColor.YELLOW + "/상점 입찰 <시작가> [즉구가]" + ChatColor.GRAY + " - 손에 든 아이템을 경매에 등록합니다.");
        player.sendMessage(ChatColor.YELLOW + "/상점 구매요청 <개당가> <수량>" + ChatColor.GRAY + " - 손에 든 아이템을 구매 요청합니다.");
        player.sendMessage(ChatColor.YELLOW + "/상점 시세" + ChatColor.GRAY + " - 손에 든 아이템의 시세를 봅니다.");
        player.sendMessage(ChatColor.YELLOW + "/상점 관리" + ChatColor.GRAY + " - 내 물품을 관리합니다.");
        player.sendMessage(ChatColor.GOLD + "--------------------");
    }

    public void processExpiredAuctions() {
        List<AuctionItem> expiredAuctions = dbManager.getExpiredActiveAuctions();
        for (AuctionItem auction : expiredAuctions) {
            if (auction.getHighestBidderUuid() != null) {
                OfflinePlayer winner = Bukkit.getOfflinePlayer(auction.getHighestBidderUuid());
                OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());

                double finalPrice = auction.getCurrentBid();
                double finalSellerAmount = finalPrice;
                if (!seller.getPlayer().hasPermission("usershop.tax.exempt")) {
                    finalSellerAmount *= 0.95;
                }

                economyManager.depositPlayer(seller.getUniqueId(), finalSellerAmount);
                if (seller.isOnline()) {
                    seller.getPlayer().sendMessage(ChatColor.GREEN + "당신의 경매 아이템 (" + auction.getItemStack().getType().name() + ")이 " + winner.getName() + "에게 " + formatter.format(finalPrice) + "원에 낙찰되어 " + formatter.format(finalSellerAmount) + "원이 지급되었습니다.");
                }

                ItemStack auctionedItem = auction.getItemStack().clone();
                auctionedItem.setAmount(1);
                giveItemToPlayer(winner, auctionedItem, "경매 낙찰 아이템");

                dbManager.saveTransaction(new Transaction(0, auction.getId(), winner.getUniqueId(), winner.getName(), seller.getUniqueId(), seller.getName(), auction.getItemStack(), finalPrice, 1, System.currentTimeMillis(), "AUCTION_PURCHASE"));
                Bukkit.getPluginManager().callEvent(new AuctionEndedEvent(auction, AuctionEndedEvent.Reason.SOLD, winner.isOnline() ? winner.getPlayer() : null));
            } else {
                OfflinePlayer seller = Bukkit.getOfflinePlayer(auction.getSellerUuid());
                if (seller.isOnline()) {
                    seller.getPlayer().sendMessage(ChatColor.YELLOW + "당신의 경매 아이템 (" + auction.getItemStack().getType().name() + ")이 입찰자 없이 만료되었습니다. 물품 관리에서 회수할 수 있습니다.");
                }
                Bukkit.getPluginManager().callEvent(new AuctionEndedEvent(auction, AuctionEndedEvent.Reason.NO_BIDS, null));
            }
            dbManager.updateAuctionItemStatus(auction.getId(), "ENDED");
        }
    }

    private void giveItemToPlayer(OfflinePlayer player, ItemStack item, String reason) {
        if (player.isOnline()) {
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer.getInventory().addItem(item).isEmpty()) {
                onlinePlayer.sendMessage(ChatColor.GREEN + reason + "을(를) 인벤토리로 지급받았습니다.");
            } else {
                sendToGiftBoxOrDrop(onlinePlayer, item, reason);
            }
        } else {
            if (giftBoxAPI != null) {
                long sevenDaysInSeconds = 7 * 24 * 60 * 60;
                giftBoxAPI.sendGift(player.getUniqueId(), item, "서버 시스템", sevenDaysInSeconds)
                        .thenRun(() -> plugin.getLogger().info("오프라인 플레이어 " + player.getName() + "에게 " + reason + "을(를) 우편함으로 보냈습니다."))
                        .exceptionally(ex -> {
                            plugin.getLogger().log(Level.SEVERE, "우편함으로 아이템 전송 중 오류 발생 (오프라인 플레이어: " + player.getName() + "): " + ex.getMessage(), ex);
                            return null;
                        });
            } else {
                plugin.getLogger().warning("오프라인 플레이어 " + player.getName() + "에게 아이템을 지급할 수 없습니다 (RangGiftBox API 없음). 수동 지급이 필요합니다: " + item.toString());
            }
        }
    }

    private void sendToGiftBoxOrDrop(Player player, ItemStack item, String reason) {
        if (giftBoxAPI != null) {
            long sevenDaysInSeconds = 7 * 24 * 60 * 60;
            giftBoxAPI.sendGift(player.getUniqueId(), item, "서버 시스템", sevenDaysInSeconds)
                    .thenRun(() -> {
                        if (player.isOnline()) {
                            player.sendMessage(ChatColor.YELLOW + "인벤토리가 가득 차서 " + reason + "이(가) 우편함으로 지급되었습니다.");
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.SEVERE, "우편함으로 아이템 전송 중 오류 발생 (플레이어: " + player.getName() + "): " + ex.getMessage(), ex);
                        if (player.isOnline()) {
                            player.sendMessage(ChatColor.RED + "아이템을 우편함으로 보내는 데 실패했습니다. 아이템이 바닥에 드롭됩니다.");
                            player.getWorld().dropItemNaturally(player.getLocation(), item);
                        }
                        return null;
                    });
        } else {
            player.getWorld().dropItemNaturally(player.getLocation(), item);
            player.sendMessage(ChatColor.YELLOW + "인벤토리가 가득 차서 " + reason + "이(가) 발밑에 떨어졌습니다.");
        }
    }

    private Comparator<ShopItem> getShopItemComparator(SortOrder sortOrder) {
        return switch (sortOrder) {
            case LATEST -> Comparator.comparing(ShopItem::getListTimestamp).reversed();
            case OLDEST -> Comparator.comparing(ShopItem::getListTimestamp);
            case PRICE_ASC -> Comparator.comparing(ShopItem::getPrice);
            case PRICE_DESC -> Comparator.comparing(ShopItem::getPrice).reversed();
            case EXPIRING_SOON -> Comparator.comparing(ShopItem::getExpiryTimestamp);
            case ALPHABETICAL_ASC -> Comparator.comparing(item -> {
                String name = item.getItemStack().hasItemMeta() && item.getItemStack().getItemMeta().hasDisplayName() ?
                        ChatColor.stripColor(item.getItemStack().getItemMeta().getDisplayName()) :
                        item.getItemStack().getType().name();
                return name.toLowerCase();
            });
            case ALPHABETICAL_DESC -> Comparator.comparing(item -> {
                String name = item.getItemStack().hasItemMeta() && item.getItemStack().getItemMeta().hasDisplayName() ?
                        ChatColor.stripColor(item.getItemStack().getItemMeta().getDisplayName()) :
                        item.getItemStack().getType().name();
                return name.toLowerCase();
            }).reversed();
        };
    }

    private ItemStack createDisplayItem(ShopItem shopItem) {
        ItemStack displayItem = shopItem.getItemStack().clone();
        displayItem.setAmount(shopItem.getAmount());
        ItemMeta meta = displayItem.getItemMeta();

        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            if (!lore.isEmpty()) lore.add(" ");
            lore.add(ChatColor.GRAY + "--------------------");
            lore.add(ChatColor.AQUA + "판매자: " + ChatColor.WHITE + shopItem.getSellerName());
            lore.add(ChatColor.AQUA + "개당 가격: " + ChatColor.WHITE + formatter.format(shopItem.getPrice()) + "원");
            lore.add(ChatColor.AQUA + "남은 수량: " + ChatColor.WHITE + shopItem.getAmount() + "개");
            lore.add(ChatColor.AQUA + "남은 시간: " + ChatColor.WHITE + formatDuration(shopItem.getExpiryTimestamp() - System.currentTimeMillis()));
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "▶ 좌클릭: 구매 확인창 열기");
            lore.add(ChatColor.YELLOW + "▶ 우클릭: 이 아이템 최저가 검색");
            lore.add(ChatColor.YELLOW + "▶ Shift+좌클릭: 상세 시세 정보 보기");
            lore.add(ChatColor.YELLOW + "▶ Shift+우클릭: 판매자 상점 보기");
            lore.add(ChatColor.GRAY + "--------------------");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(shopItemIdKey, PersistentDataType.INTEGER, shopItem.getId());
            meta.getPersistentDataContainer().set(shopSellerUuidKey, PersistentDataType.STRING, shopItem.getSellerUuid().toString());
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }

    private ItemStack createDisplayItem(AuctionItem auctionItem) {
        ItemStack displayItem = auctionItem.getItemStack().clone();
        displayItem.setAmount(1);
        ItemMeta meta = displayItem.getItemMeta();

        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            if (!lore.isEmpty()) lore.add(" ");
            lore.add(ChatColor.GRAY + "--------------------");
            lore.add(ChatColor.AQUA + "판매자: " + ChatColor.WHITE + auctionItem.getSellerName());
            lore.add(ChatColor.AQUA + "시작가: " + ChatColor.WHITE + formatter.format(auctionItem.getStartPrice()) + "원");
            lore.add(ChatColor.AQUA + "현재 입찰가: " + ChatColor.WHITE + formatter.format(auctionItem.getCurrentBid()) + "원");
            if (auctionItem.getHighestBidderName() != null) {
                lore.add(ChatColor.AQUA + "최고 입찰자: " + ChatColor.WHITE + auctionItem.getHighestBidderName());
            }
            if (auctionItem.getBuyNowPrice() > 0) {
                lore.add(ChatColor.AQUA + "즉시 구매가: " + ChatColor.WHITE + formatter.format(auctionItem.getBuyNowPrice()) + "원");
            }
            lore.add(ChatColor.AQUA + "남은 시간: " + ChatColor.WHITE + formatDuration(auctionItem.getEndTimestamp() - System.currentTimeMillis()));
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "▶ 좌클릭: 입찰하기");
            if (auctionItem.getBuyNowPrice() > 0) {
                lore.add(ChatColor.YELLOW + "▶ 우클릭: 즉시 구매하기");
            }
            lore.add(ChatColor.YELLOW + "▶ Shift+우클릭: 판매자 경매 보기");
            lore.add(ChatColor.GRAY + "--------------------");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(auctionItemIdKey, PersistentDataType.INTEGER, auctionItem.getId());
            meta.getPersistentDataContainer().set(shopSellerUuidKey, PersistentDataType.STRING, auctionItem.getSellerUuid().toString());
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }

    private ItemStack createDisplayItem(BuyRequest buyRequest) {
        ItemStack displayItem = buyRequest.getItemStack().clone();
        displayItem.setAmount(buyRequest.getAmountRequested() - buyRequest.getAmountFulfilled());
        ItemMeta meta = displayItem.getItemMeta();

        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            if (!lore.isEmpty()) lore.add(" ");
            lore.add(ChatColor.GRAY + "--------------------");
            lore.add(ChatColor.AQUA + "요청자: " + ChatColor.WHITE + buyRequest.getRequesterName());
            lore.add(ChatColor.AQUA + "개당 가격: " + ChatColor.WHITE + formatter.format(buyRequest.getPricePerItem()) + "원");
            lore.add(ChatColor.AQUA + "요청 수량: " + ChatColor.WHITE + buyRequest.getAmountRequested() + "개");
            lore.add(ChatColor.AQUA + "남은 수량: " + ChatColor.WHITE + (buyRequest.getAmountRequested() - buyRequest.getAmountFulfilled()) + "개");
            lore.add(" ");
            lore.add(ChatColor.YELLOW + "▶ 좌클릭: 판매하기");
            lore.add(ChatColor.YELLOW + "▶ Shift+우클릭: 요청자 구매 요청 보기");
            lore.add(ChatColor.GRAY + "--------------------");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(buyRequestIdKey, PersistentDataType.INTEGER, buyRequest.getId());
            meta.getPersistentDataContainer().set(shopSellerUuidKey, PersistentDataType.STRING, buyRequest.getRequesterUuid().toString());
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }

    private ItemStack createManagementDisplayItem(ShopItem shopItem, ManagementTab tab) {
        ItemStack displayItem = shopItem.getItemStack().clone();
        displayItem.setAmount(shopItem.getAmount());
        ItemMeta meta = displayItem.getItemMeta();

        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            if (!lore.isEmpty()) lore.add(" ");
            lore.add(ChatColor.GRAY + "--------------------");

            lore.add(ChatColor.AQUA + "개당 가격: " + ChatColor.WHITE + formatter.format(shopItem.getPrice()) + "원");
            lore.add(ChatColor.AQUA + "수량: " + ChatColor.WHITE + shopItem.getAmount() + "개");

            if (tab == ManagementTab.SELLING) {
                lore.add(ChatColor.AQUA + "남은 시간: " + ChatColor.WHITE + formatDuration(shopItem.getExpiryTimestamp() - System.currentTimeMillis()));
                lore.add(" ");
                lore.add(ChatColor.RED + "▶ 좌클릭: 판매 취소하기");
            } else if (tab == ManagementTab.SOLD_EXPIRED) {
                String status = switch (shopItem.getStatus()) {
                    case "SOLD" -> "판매 완료";
                    case "EXPIRED" -> "만료됨";
                    case "CANCELLED" -> "판매 취소됨";
                    default -> shopItem.getStatus();
                };
                lore.add(ChatColor.AQUA + "상태: " + ChatColor.WHITE + status);
                lore.add(ChatColor.AQUA + "종료 시간: " + ChatColor.WHITE + new Date(shopItem.getExpiryTimestamp()).toLocaleString());
                lore.add(" ");
                if (shopItem.getStatus().equals("EXPIRED") || shopItem.getStatus().equals("CANCELLED")) {
                    lore.add(ChatColor.GREEN + "▶ 좌클릭: 아이템 회수하기");
                } else {
                    lore.add(ChatColor.GRAY + "판매 대금은 자동으로 정산되었습니다.");
                }
            }
            lore.add(ChatColor.GRAY + "--------------------");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(shopItemIdKey, PersistentDataType.INTEGER, shopItem.getId());
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }

    private ItemStack createManagementDisplayItem(AuctionItem auctionItem, ManagementTab tab) {
        ItemStack displayItem = auctionItem.getItemStack().clone();
        displayItem.setAmount(1);
        ItemMeta meta = displayItem.getItemMeta();

        if (meta != null) {
            List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();
            if (!lore.isEmpty()) lore.add(" ");
            lore.add(ChatColor.GRAY + "--------------------");
            lore.add(ChatColor.AQUA + "시작가: " + ChatColor.WHITE + formatter.format(auctionItem.getStartPrice()) + "원");
            lore.add(ChatColor.AQUA + "최종 입찰가: " + ChatColor.WHITE + formatter.format(auctionItem.getCurrentBid()) + "원");
            if (auctionItem.getHighestBidderName() != null) {
                lore.add(ChatColor.AQUA + "최고 입찰자: " + ChatColor.WHITE + auctionItem.getHighestBidderName());
            }

            if (tab == ManagementTab.SELLING) {
                lore.add(ChatColor.AQUA + "남은 시간: " + ChatColor.WHITE + formatDuration(auctionItem.getEndTimestamp() - System.currentTimeMillis()));
                lore.add(" ");
                lore.add(ChatColor.RED + "▶ 좌클릭: 경매 취소하기");
            } else if (tab == ManagementTab.SOLD_EXPIRED) {
                String status = switch (auctionItem.getStatus()) {
                    case "ENDED" -> auctionItem.getHighestBidderName() != null ? "경매 종료 (낙찰)" : "경매 종료 (유찰)";
                    case "CANCELLED" -> "경매 취소됨";
                    default -> auctionItem.getStatus();
                };
                lore.add(ChatColor.AQUA + "상태: " + ChatColor.WHITE + status);
                lore.add(ChatColor.AQUA + "종료 시간: " + ChatColor.WHITE + new Date(auctionItem.getEndTimestamp()).toLocaleString());
                lore.add(" ");
                lore.add(ChatColor.GREEN + "▶ 좌클릭: 아이템/대금 회수하기");
            }
            lore.add(ChatColor.GRAY + "--------------------");
            meta.setLore(lore);

            meta.getPersistentDataContainer().set(auctionItemIdKey, PersistentDataType.INTEGER, auctionItem.getId());
            displayItem.setItemMeta(meta);
        }
        return displayItem;
    }

    private void addMainNavigationBar(Player player, Inventory gui, MainGuiTab currentTab, int currentPage, int totalPages) {
        gui.setItem(MAIN_TAB_SHOP_SLOT, createNavItem(MainGuiTab.SHOP.getIcon(), (currentTab == MainGuiTab.SHOP ? ChatColor.GREEN : ChatColor.YELLOW) + MainGuiTab.SHOP.getDisplayName(), ChatColor.GRAY + "일반 상점을 봅니다."));
        gui.setItem(MAIN_TAB_AUCTION_SLOT, createNavItem(MainGuiTab.AUCTION.getIcon(), (currentTab == MainGuiTab.AUCTION ? ChatColor.GREEN : ChatColor.YELLOW) + MainGuiTab.AUCTION.getDisplayName(), ChatColor.GRAY + "경매장을 봅니다."));
        gui.setItem(MAIN_TAB_BUY_REQUEST_SLOT, createNavItem(MainGuiTab.BUY_REQUESTS.getIcon(), (currentTab == MainGuiTab.BUY_REQUESTS ? ChatColor.GREEN : ChatColor.YELLOW) + MainGuiTab.BUY_REQUESTS.getDisplayName(), ChatColor.GRAY + "구매 요청 목록을 봅니다."));

        if (currentPage > 1) {
            gui.setItem(MAIN_PREV_PAGE_SLOT, createNavItem(Material.ARROW, ChatColor.GREEN + "이전 페이지", "클릭하여 " + (currentPage - 1) + "페이지로 이동합니다."));
        } else {
            gui.setItem(MAIN_PREV_PAGE_SLOT, createNavItem(Material.GRAY_STAINED_GLASS_PANE, " ", ""));
        }

        gui.setItem(MAIN_PAGE_INFO_SLOT, createNavItem(Material.PAPER, ChatColor.AQUA + "페이지 정보", ChatColor.WHITE + String.valueOf(currentPage) + " / " + totalPages));

        if (currentPage < totalPages) {
            gui.setItem(MAIN_NEXT_PAGE_SLOT, createNavItem(Material.ARROW, ChatColor.GREEN + "다음 페이지", "클릭하여 " + (currentPage + 1) + "페이지로 이동합니다."));
        } else {
            gui.setItem(MAIN_NEXT_PAGE_SLOT, createNavItem(Material.GRAY_STAINED_GLASS_PANE, " ", ""));
        }

        gui.setItem(MAIN_REFRESH_SLOT, createNavItem(Material.SUNFLOWER, ChatColor.YELLOW + "새로고침", "클릭하여 상점 목록을 새로고침합니다."));

        UUID filterSellerUuid = plugin.getPlayerFilterSellerUuid(player.getUniqueId());
        if (filterSellerUuid != null) {
            OfflinePlayer filteredPlayer = Bukkit.getOfflinePlayer(filterSellerUuid);
            gui.setItem(MAIN_SEARCH_HELP_SLOT, createNavItem(Material.REDSTONE_BLOCK, ChatColor.RED + "필터 활성화됨: " + (filteredPlayer.hasPlayedBefore() ? filteredPlayer.getName() : "알 수 없음"),
                    ChatColor.GRAY + "Shift+좌클릭: 필터 초기화",
                    ChatColor.GRAY + "좌클릭: 검색",
                    ChatColor.GRAY + "우클릭: 도움말"));
        } else {
            gui.setItem(MAIN_SEARCH_HELP_SLOT, createNavItem(Material.OAK_SIGN, ChatColor.YELLOW + "검색 / 도움말",
                    ChatColor.GRAY + "좌클릭: 검색",
                    ChatColor.GRAY + "우클릭: 도움말"));
        }

        gui.setItem(MAIN_SORT_SLOT, createNavItem(Material.HOPPER, ChatColor.YELLOW + "정렬: " + plugin.getCurrentSortOrder().getDisplayName(), "클릭하여 정렬 방식을 변경합니다."));
    }

    private void addManagementNavigationBar(Inventory gui, ManagementTab currentTab, int currentPage, int totalPages) {
        for (ManagementTab tab : ManagementTab.values()) {
            ItemStack tabItem = createNavItem(tab.getIcon(),
                    (currentTab == tab ? ChatColor.GREEN : ChatColor.YELLOW) + tab.getDisplayName(),
                    ChatColor.GRAY + "클릭하여 " + tab.getDisplayName() + "을(를) 봅니다.");
            gui.setItem(getManagementTabSlot(tab), tabItem);
        }

        if (currentPage > 1) {
            gui.setItem(MANAGEMENT_PREV_PAGE_SLOT, createNavItem(Material.ARROW, ChatColor.GREEN + "이전 페이지", "클릭하여 " + (currentPage - 1) + "페이지로 이동합니다."));
        } else {
            gui.setItem(MANAGEMENT_PREV_PAGE_SLOT, createNavItem(Material.GRAY_STAINED_GLASS_PANE, " ", ""));
        }

        gui.setItem(MANAGEMENT_PAGE_INFO_SLOT, createNavItem(Material.PAPER, ChatColor.AQUA + "페이지 정보", currentPage + " / " + totalPages));

        if (currentPage < totalPages) {
            gui.setItem(MANAGEMENT_NEXT_PAGE_SLOT, createNavItem(Material.ARROW, ChatColor.GREEN + "다음 페이지", "클릭하여 " + (currentPage + 1) + "페이지로 이동합니다."));
        } else {
            gui.setItem(MANAGEMENT_NEXT_PAGE_SLOT, createNavItem(Material.GRAY_STAINED_GLASS_PANE, " ", ""));
        }

        gui.setItem(MANAGEMENT_BACK_TO_MAIN_SLOT, createNavItem(Material.WRITABLE_BOOK, ChatColor.YELLOW + "메인 상점으로 돌아가기", "클릭하여 메인 상점으로 돌아갑니다."));
    }

    private int getManagementTabSlot(ManagementTab tab) {
        return switch (tab) {
            case SELLING -> MANAGEMENT_TAB_SELLING_SLOT;
            case SOLD_EXPIRED -> MANAGEMENT_TAB_SOLD_EXPIRED_SLOT;
            case STORAGE_INFO -> MANAGEMENT_TAB_STORAGE_INFO_SLOT;
        };
    }

    private ItemStack createNavItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String formatDuration(long millis) {
        if (millis < 0) return "만료됨";
        long days = TimeUnit.MILLISECONDS.toDays(millis);
        millis -= TimeUnit.DAYS.toMillis(days);
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        millis -= TimeUnit.HOURS.toMillis(hours);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);

        StringBuilder sb = new StringBuilder();
        if (days > 0) sb.append(days).append("일 ");
        if (hours > 0) sb.append(hours).append("시간 ");
        if (minutes > 0) sb.append(minutes).append("분");
        if (sb.length() == 0) return "곧 만료";
        return sb.toString().trim();
    }

    private int extractShopItemId(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.has(shopItemIdKey, PersistentDataType.INTEGER)) {
                    return container.getOrDefault(shopItemIdKey, PersistentDataType.INTEGER, -1);
                }
            }
        }
        return -1;
    }

    private int extractAuctionItemId(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.has(auctionItemIdKey, PersistentDataType.INTEGER)) {
                    return container.getOrDefault(auctionItemIdKey, PersistentDataType.INTEGER, -1);
                }
            }
        }
        return -1;
    }

    private int extractBuyRequestId(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.has(buyRequestIdKey, PersistentDataType.INTEGER)) {
                    return container.getOrDefault(buyRequestIdKey, PersistentDataType.INTEGER, -1);
                }
            }
        }
        return -1;
    }

    private UUID extractSellerUuid(ItemStack item) {
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.has(shopSellerUuidKey, PersistentDataType.STRING)) {
                    String uuidStr = container.get(shopSellerUuidKey, PersistentDataType.STRING);
                    try {
                        return UUID.fromString(uuidStr);
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().log(Level.WARNING, "Invalid UUID string in item PDC: " + uuidStr, e);
                    }
                }
            }
        }
        return null;
    }
}