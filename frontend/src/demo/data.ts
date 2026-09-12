import type { Branch, Menu, Restaurant, Zone } from '../api/contracts'

export type RestaurantPresentation = {
  cuisine?: string
  rating?: number
  ratingCount?: string
  badge?: string
  art: 'burger' | 'pizza' | 'egyptian' | 'breakfast'
}

export const demoZones: Zone[] = [
  { id: 'zone-zamalek', name: 'Zamalek', cityId: 'city-cairo', cityName: 'Cairo' },
  { id: 'zone-maadi', name: 'Maadi', cityId: 'city-cairo', cityName: 'Cairo' },
  { id: 'zone-heliopolis', name: 'Heliopolis', cityId: 'city-cairo', cityName: 'Cairo' },
]

export const demoRestaurants: Restaurant[] = [
  { id: 'butchers-burger', name: "Butcher's Burger", description: 'Smash burgers, loaded fries and big flavour.', branchCount: 4, openNow: true, serviceable: true, minimumDeliveryFee: 15, minimumOrder: 80, minimumEtaMinutes: 25, currency: 'EGP' },
  { id: 'what-the-crust', name: 'What The Crust', description: 'Neapolitan pizza fired hot and served fast.', branchCount: 2, openNow: true, serviceable: true, minimumDeliveryFee: 0, minimumOrder: 100, minimumEtaMinutes: 30, currency: 'EGP' },
  { id: 'semsema-1970', name: 'Semsema 1970', description: 'Cairo classics, shawarma and grilled favourites.', branchCount: 7, openNow: true, serviceable: true, minimumDeliveryFee: 12, minimumOrder: 70, minimumEtaMinutes: 20, currency: 'EGP' },
  { id: 'holm-cafe', name: 'Holm Cafe', description: 'Coffee, breakfast and all-day comfort plates.', branchCount: 3, openNow: false, serviceable: true, minimumDeliveryFee: 0, minimumOrder: 90, minimumEtaMinutes: 35, currency: 'EGP' },
  { id: 'zooba', name: 'Zooba', description: 'Modern Egyptian street food made with local ingredients.', branchCount: 6, openNow: true, serviceable: true, minimumDeliveryFee: 15, minimumOrder: 85, minimumEtaMinutes: 25, currency: 'EGP' },
  { id: 'maison-thomas', name: 'Maison Thomas', description: 'Cairo pizza institution with timeless favourites.', branchCount: 3, openNow: true, serviceable: true, minimumDeliveryFee: 10, minimumOrder: 120, minimumEtaMinutes: 35, currency: 'EGP' },
]

export const demoPresentation: Record<string, RestaurantPresentation> = {
  'butchers-burger': { cuisine: 'Burgers · American', rating: 4.8, ratingCount: '2.4k', badge: 'Top rated', art: 'burger' },
  'what-the-crust': { cuisine: 'Pizza · Italian', rating: 4.7, ratingCount: '1.8k', badge: 'Free delivery', art: 'pizza' },
  'semsema-1970': { cuisine: 'Egyptian · Shawarma', rating: 4.6, ratingCount: '3.1k', art: 'egyptian' },
  'holm-cafe': { cuisine: 'Breakfast · Cafe', rating: 4.5, ratingCount: '920', art: 'breakfast' },
  zooba: { cuisine: 'Egyptian · Street food', rating: 4.7, ratingCount: '1.5k', art: 'egyptian' },
  'maison-thomas': { cuisine: 'Pizza · Italian', rating: 4.6, ratingCount: '1.1k', art: 'pizza' },
}

export const demoBranch: Branch = {
  id: 'butchers-zamalek', name: 'Zamalek', addressLine1: '15 Hassan Sabry Street', city: 'Cairo', timezone: 'Africa/Cairo', openNow: true, state: 'ACTIVE', serviceable: true, deliveryFee: 15, minimumOrder: 80, etaMinMinutes: 25, etaMaxMinutes: 35, currency: 'EGP',
}

const page = <T,>(items: T[]) => ({ items, page: 0, size: 100, total: items.length })

export const demoMenu: Menu = {
  id: 'menu-butchers-zamalek',
  branchId: demoBranch.id,
  name: 'All day menu',
  currency: 'EGP',
  categories: page([
    {
      id: 'cat-popular', name: 'Popular', description: 'The favourites everyone comes back for.', items: page([
        { id: 'double-smash', name: 'Double Wagyu Smash', description: 'Two wagyu patties, cheddar, pickles, caramelised onions and house sauce.', effectivePrice: 195, effectiveAvailability: true },
        { id: 'classic-smash', name: 'Classic Smash Burger', description: 'Beef patty, American cheese, lettuce, pickles and burger sauce.', effectivePrice: 145, effectiveAvailability: true },
        { id: 'loaded-fries', name: 'Butcher’s Loaded Fries', description: 'Crispy fries, chopped beef, cheddar sauce, jalapeños and ranch.', effectivePrice: 115, effectiveAvailability: true },
      ]),
    },
    {
      id: 'cat-burgers', name: 'Burgers', description: 'Hand-smashed and cooked to order.', items: page([
        { id: 'truffle-smash', name: 'Truffle Mushroom Smash', description: 'Beef, Swiss cheese, sautéed mushrooms and truffle mayo.', effectivePrice: 175, effectiveAvailability: true },
        { id: 'spicy-chicken', name: 'Hot Honey Chicken', description: 'Crispy chicken, slaw, pickles and hot honey glaze.', effectivePrice: 155, effectiveAvailability: true },
        { id: 'smokehouse', name: 'Smokehouse BBQ', description: 'Double beef, smoked cheddar, crispy onions and barbecue sauce.', effectivePrice: 185, effectiveAvailability: false },
      ]),
    },
    {
      id: 'cat-sides', name: 'Sides & drinks', description: 'The perfect extras.', items: page([
        { id: 'fries', name: 'Sea Salt Fries', description: 'Golden skin-on fries with sea salt.', effectivePrice: 55, effectiveAvailability: true },
        { id: 'lemonade', name: 'Fresh Lemonade', description: 'House-made with lemon and mint.', effectivePrice: 45, effectiveAvailability: true },
      ]),
    },
  ]),
}
